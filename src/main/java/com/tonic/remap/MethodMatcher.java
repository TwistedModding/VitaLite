package com.tonic.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MethodMatcher {
    public static class Match {
        public final MethodKey oldKey;
        public final MethodKey newKey;
        public final double score;

        public Match(MethodKey oldKey, MethodKey newKey, double score) {
            this.oldKey = oldKey;
            this.newKey = newKey;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("%s -> %s : %.3f", oldKey, newKey, score);
        }
    }

    // --- plumbing canonical tokens ---
    private static final String EXCEPTION_WRAP_TOKEN = "EXCEPTION_WRAP";
    private static final String STRING_BUILDER_COLLAPSE_TOKEN = "STRING_BUILDER_TO_STRING";

    private static final Set<String> EXCEPTION_WRAP_SIGS = Set.of(
            "ln.an(Ljava/lang/Throwable;Ljava/lang/String;)Lwu;",
            "cy.aj(Ljava/lang/Throwable;Ljava/lang/String;)Lxi;"
    );
    private static final Set<String> STRING_BUILDER_CHAIN_SIGS = Set.of(
            "java/lang/StringBuilder.<init>()V",
            "java/lang/StringBuilder.append(C)Ljava/lang/StringBuilder;",
            "java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            "java/lang/StringBuilder.toString()Ljava/lang/String;"
    );

    // cache for extracted field pattern tokens so we don't re-reflect/recompute repeatedly
    private static final ConcurrentMap<NormalizedMethod, Set<String>> fieldPatternCache = new ConcurrentHashMap<>();

    /**
     * Single-threaded compatibility entrypoint.
     */
    public static List<Match> matchAll(
            Map<MethodKey, NormalizedMethod> oldMethods,
            Map<MethodKey, NormalizedMethod> newMethods,
            int topKPerOld
    ) {
        return matchAllConcurrent(oldMethods, newMethods, topKPerOld, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Class-aware entrypoint. If you have class matches, supply them along with a classWeight in [0,1]
     * to bias score when method owners align.
     */
    public static List<Match> matchAll(
            Map<MethodKey, NormalizedMethod> oldMethods,
            Map<MethodKey, NormalizedMethod> newMethods,
            Map<String, ClassMatcher.ClassMatch> classMatchByOldOwner,
            int topKPerOld,
            double classWeight
    ) {
        return matchAllWithClassContext(oldMethods, newMethods, classMatchByOldOwner, topKPerOld,
                Runtime.getRuntime().availableProcessors(), classWeight);
    }

    public static List<Match> matchAllWithClassContext(
            Map<MethodKey, NormalizedMethod> oldMethods,
            Map<MethodKey, NormalizedMethod> newMethods,
            Map<String, ClassMatcher.ClassMatch> classMatchByOldOwner,
            int topKPerOld,
            int threads,
            double classWeight // in [0,1], how much to boost when class owners align
    ) {
        if (oldMethods.isEmpty() || newMethods.isEmpty() || topKPerOld <= 0) {
            return Collections.emptyList();
        }

        List<NormalizedMethod> corpus = new ArrayList<>(oldMethods.values());
        corpus.addAll(newMethods.values());
        CorpusStats stats = buildCorpusStats(corpus);

        long totalPairs = (long) oldMethods.size() * (long) newMethods.size();
        ProgressBar progressBar = new ProgressBar(totalPairs, 40);
        AtomicLong processed = new AtomicLong(0);
        ConcurrentLinkedQueue<Match> collected = new ConcurrentLinkedQueue<>();

        ExecutorService exec = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Map.Entry<MethodKey, NormalizedMethod> oEntry : oldMethods.entrySet()) {
                futures.add(exec.submit(() -> {
                    MethodKey oldKey = oEntry.getKey();
                    NormalizedMethod oldNorm = oEntry.getValue();

                    PriorityQueue<Match> topK =
                            new PriorityQueue<>(topKPerOld, Comparator.comparingDouble(m -> m.score));

                    ClassMatcher.ClassMatch classMatch = classMatchByOldOwner.get(oldKey.owner);
                    String expectedNewOwner = classMatch != null ? classMatch.newFp.internalName : null;

                    for (Map.Entry<MethodKey, NormalizedMethod> nEntry : newMethods.entrySet()) {
                        MethodKey newKey = nEntry.getKey();
                        NormalizedMethod newNorm = nEntry.getValue();

                        long curr = processed.incrementAndGet(); // always count the pair

                        // existing structural filters
                        if ((oldKey.name.length() == 2) != (newKey.name.length() == 2)) {
                            if (shouldUpdateProgress(curr, totalPairs)) {
                                synchronized (progressBar) { progressBar.update(curr); }
                            }
                            continue;
                        }
                        if (!compatibleObjPrimPattern(oldKey.desc, newKey.desc)) {
                            if (shouldUpdateProgress(curr, totalPairs)) {
                                synchronized (progressBar) { progressBar.update(curr); }
                            }
                            continue;
                        }

                        double baseScore = score(oldNorm, newNorm, stats);
                        double finalScore = baseScore;

                        // soft boost only when class owners align; do not penalize otherwise
                        if (expectedNewOwner != null && newKey.owner.equals(expectedNewOwner)) {
                            finalScore = baseScore * (1.0 - classWeight) + classWeight;
                        }

                        Match m = new Match(oldKey, newKey, finalScore);

                        if (topK.size() < topKPerOld) {
                            topK.offer(m);
                        } else if (finalScore > Objects.requireNonNull(topK.peek()).score) {
                            topK.poll();
                            topK.offer(m);
                        }

                        if (shouldUpdateProgress(curr, totalPairs)) {
                            synchronized (progressBar) { progressBar.update(curr); }
                        }
                    }

                    while (!topK.isEmpty()) {
                        collected.add(topK.poll());
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (ExecutionException | InterruptedException ignored) { }
            }
        } finally {
            exec.shutdownNow();
        }

        List<Match> result = new ArrayList<>(collected);
        result.sort(Comparator.comparingDouble((Match m) -> -m.score));
        synchronized (progressBar) { progressBar.update(totalPairs); }
        return result;
    }

    /**
     * Helper to centralize progress update condition (mirrors prior logic).
     */
    private static boolean shouldUpdateProgress(long curr, long totalPairs) {
        return curr % Math.max(1, totalPairs / 100) == 0 ||
                curr % 1000 == 0 ||
                curr == totalPairs;
    }

    /**
     * Parallelized top-K matcher with corpus-level stats.
     */
    public static List<Match> matchAllConcurrent(
            Map<MethodKey, NormalizedMethod> oldMethods,
            Map<MethodKey, NormalizedMethod> newMethods,
            int topKPerOld,
            int threads
    ) {
        if (oldMethods.isEmpty() || newMethods.isEmpty() || topKPerOld <= 0) {
            return Collections.emptyList();
        }

        // Build corpus stats once
        List<NormalizedMethod> corpus = new ArrayList<>();
        corpus.addAll(oldMethods.values());
        corpus.addAll(newMethods.values());
        CorpusStats stats = buildCorpusStats(corpus);

        long totalPairs = (long) oldMethods.size() * (long) newMethods.size();
        ProgressBar progressBar = new ProgressBar(totalPairs, 40);
        AtomicLong processed  = new AtomicLong(0);
        ConcurrentLinkedQueue<Match> collected = new ConcurrentLinkedQueue<>();

        ExecutorService exec = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Map.Entry<MethodKey, NormalizedMethod> oEntry : oldMethods.entrySet()) {
                futures.add(exec.submit(() -> {
                    MethodKey        oldKey  = oEntry.getKey();
                    NormalizedMethod oldNorm = oEntry.getValue();

                    PriorityQueue<Match> topK =
                            new PriorityQueue<>(topKPerOld, Comparator.comparingDouble(m -> m.score));

                    for (Map.Entry<MethodKey, NormalizedMethod> nEntry : newMethods.entrySet()) {
                        MethodKey        newKey  = nEntry.getKey();
                        NormalizedMethod newNorm = nEntry.getValue();

                        /* ─── rule 1: reject if name-length mismatch (exactly 2 vs not-2) ─── */
                        if ((oldKey.name.length() == 2) != (newKey.name.length() == 2)) {
                            processed.incrementAndGet();   // still advance progress
                            continue;
                        }

                        /* ─── rule 2: reject if obj/prim parameter pattern differs ─── */
                        if (!compatibleObjPrimPattern(oldKey.desc, newKey.desc)) {
                            processed.incrementAndGet();
                            continue;
                        }

                        double s = score(oldNorm, newNorm, stats);
                        Match  m = new Match(oldKey, newKey, s);

                        if (topK.size() < topKPerOld) {
                            topK.offer(m);
                        } else if (s > Objects.requireNonNull(topK.peek()).score) {
                            topK.poll();
                            topK.offer(m);
                        }

                        long curr = processed.incrementAndGet();
                        if (curr % Math.max(1, totalPairs / 100) == 0 ||
                                curr % 1000 == 0 ||
                                curr == totalPairs) {
                            synchronized (progressBar) { progressBar.update(curr); }
                        }
                    }

                    while (!topK.isEmpty()) {
                        collected.add(topK.poll());
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (ExecutionException | InterruptedException ignored) { }
            }
        } finally {
            exec.shutdownNow();
        }

        List<Match> result = new ArrayList<>(collected);
        result.sort(Comparator.comparingDouble((Match m) -> -m.score));
        synchronized (progressBar) { progressBar.update(totalPairs); }
        return result;
    }

    /**
     * Backwards-compatible wrapper for scoring without global corpus.
     */
    public static double score(NormalizedMethod a, NormalizedMethod b) {
        CorpusStats stats = buildCorpusStats(List.of(a, b));
        return score(a, b, stats);
    }

    /**
     * Core scoring combining descriptor, invoked signatures (IDF-weighted), string constants, opcode overlap,
     * field-operation structural similarity, and complexity/boilerplate adjustments.
     */
    public static double score(NormalizedMethod a, NormalizedMethod b, CorpusStats stats) {
        double score = 0.0;

        // Descriptor exact match
        if (a.normalizedDescriptor.equals(b.normalizedDescriptor)) {
            score += 1.0;
        }

        // Invoked signatures (plumbing normalized + IDF-weighted)
        double invokedSim = weightedJaccard(
                stats.invokedIdf,
                normalizePlumbing(a.invokedSignatures),
                normalizePlumbing(b.invokedSignatures)
        );
        score += invokedSim * 0.5;

        // String constants similarity
        double stringSim = weightedJaccard(
                stats.stringIdf,
                a.stringConstants,
                b.stringConstants
        );
        score += stringSim * 0.3;

        // Opcode overlap
        score += overlapCoefficient(a.opcodeHistogram.keySet(), b.opcodeHistogram.keySet()) * 0.2;

        // Field-pattern structural similarity (heuristic)
        Set<String> fieldPatternA = extractFieldPatternTokens(a);
        Set<String> fieldPatternB = extractFieldPatternTokens(b);
        double fieldPatternSim = jaccard(fieldPatternA, fieldPatternB);
        score += fieldPatternSim * 0.7; // substantial boost for matching structural field ops

        // Complexity factor (still lowers weight for trivial signatures)
        double complexityA = complexityFactor(a);
        double complexityB = complexityFactor(b);
        double complexity = Math.min(complexityA, complexityB);
        double complexityMultiplier = 0.5 + 0.5 * complexity;
        score *= complexityMultiplier;

        // Boilerplate / plumbing-only penalty
        boolean aBoiler = isPlumbingOnly(a, normalizePlumbing(a.invokedSignatures), fieldPatternA);
        boolean bBoiler = isPlumbingOnly(b, normalizePlumbing(b.invokedSignatures), fieldPatternB);
        if (aBoiler && bBoiler) {
            score *= 0.2; // both are trivial plumbing: very small
        } else if (aBoiler || bBoiler) {
            score *= 0.4; // one side is boilerplate: penalize
        }

        return score;
    }

    /**
     * Collapse known plumbing/boilerplate invoked signatures to canonical tokens.
     */
    private static Set<String> normalizePlumbing(Set<String> original) {
        Set<String> processed = new HashSet<>(original);

        boolean hasExceptionWrap = false;
        for (String s : EXCEPTION_WRAP_SIGS) {
            if (processed.contains(s)) {
                hasExceptionWrap = true;
                processed.remove(s);
            }
        }
        if (hasExceptionWrap) {
            processed.add(EXCEPTION_WRAP_TOKEN);
        }

        boolean hasSb = false;
        for (String s : STRING_BUILDER_CHAIN_SIGS) {
            if (processed.contains(s)) {
                hasSb = true;
                processed.remove(s);
            }
        }
        if (hasSb) {
            processed.add(STRING_BUILDER_COLLAPSE_TOKEN);
        }

        return processed;
    }

    /**
     * Weighted Jaccard using per-token IDF weights.
     */
    static double weightedJaccard(Map<String, Double> idf, Set<String> s1, Set<String> s2) {
        if ((s1.isEmpty() && s2.isEmpty())) return 1.0;
        Set<String> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);
        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        if (union.isEmpty()) return 0.0;

        double num = 0.0;
        double den = 0.0;
        for (String k : intersection) {
            num += idf.getOrDefault(k, 1.0);
        }
        for (String k : union) {
            den += idf.getOrDefault(k, 1.0);
        }
        return den == 0.0 ? 0.0 : num / den;
    }

    /**
     * Simple Jaccard for sets.
     */
    static double jaccard(Set<String> s1, Set<String> s2) {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(s1);
        inter.retainAll(s2);
        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        if (union.isEmpty()) return 0.0;
        return (double) inter.size() / union.size();
    }

    /**
     * Overlap coefficient for arbitrarily-typed sets.
     */
    static double overlapCoefficient(Set<?> a, Set<?> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<Object> small = a.size() <= b.size() ? new HashSet<>(a) : new HashSet<>(b);
        Set<Object> large = a.size() > b.size() ? new HashSet<>(a) : new HashSet<>(b);
        small.retainAll(large);
        return (double) small.size() / Math.min(a.size(), b.size());
    }

    /**
     * Complexity heuristic from plumbing-normalized invoked signatures + string constants.
     * Returns in [0,1].
     */
    private static double complexityFactor(NormalizedMethod m) {
        int invoked = normalizePlumbing(m.invokedSignatures).size();
        int strings = m.stringConstants.size();
        int total = invoked + strings;
        double raw = (double) total / 5.0; // treat 5+ signals as full richness
        return Math.min(1.0, raw);
    }

    /**
     * Determine whether the method is effectively just plumbing (only plumbing tokens, no strings, no structural field ops).
     */
    private static boolean isPlumbingOnly(NormalizedMethod m, Set<String> plumbingCollapsedInvoked, Set<String> fieldPatternTokens) {
        boolean onlyPlumbingInvoked = true;
        for (String s : plumbingCollapsedInvoked) {
            if (!s.equals(EXCEPTION_WRAP_TOKEN) && !s.equals(STRING_BUILDER_COLLAPSE_TOKEN)) {
                onlyPlumbingInvoked = false;
                break;
            }
        }
        boolean noString = m.stringConstants.isEmpty();
        boolean noFieldPattern = fieldPatternTokens.isEmpty();
        return onlyPlumbingInvoked && noString && noFieldPattern;
    }

    /**
     * Build IDF-style corpus stats.
     */
    private static CorpusStats buildCorpusStats(Collection<NormalizedMethod> methods) {
        Map<String, Integer> invokedDf = new HashMap<>();
        Map<String, Integer> stringDf = new HashMap<>();
        int N = methods.size();

        for (NormalizedMethod m : methods) {
            Set<String> invoked = normalizePlumbing(m.invokedSignatures);
            for (String sig : invoked) {
                invokedDf.merge(sig, 1, Integer::sum);
            }
            for (String s : m.stringConstants) {
                stringDf.merge(s, 1, Integer::sum);
            }
        }

        Map<String, Double> invokedIdf = new HashMap<>();
        Map<String, Double> stringIdf = new HashMap<>();

        for (Map.Entry<String, Integer> e : invokedDf.entrySet()) {
            double df = e.getValue();
            invokedIdf.put(e.getKey(), Math.log((N + 1.0) / (df + 1.0)) + 1.0);
        }
        for (Map.Entry<String, Integer> e : stringDf.entrySet()) {
            double df = e.getValue();
            stringIdf.put(e.getKey(), Math.log((N + 1.0) / (df + 1.0)) + 1.0);
        }

        return new CorpusStats(invokedIdf, stringIdf);
    }

    /**
     * Heuristic structural field-operation pattern extractor.
     * Captures things like: helper call on first argument, argument field gets updated/multiplied, zeroing, this accumulation.
     */
    private static Set<String> extractFieldPatternTokens(NormalizedMethod m) {
        return fieldPatternCache.computeIfAbsent(m, nm -> {
            Set<String> tokens = new HashSet<>();
            MethodNode mn = null;

            // attempt reflection to get underlying MethodNode (assumes field named "methodNode" or "node")
            try {
                Field f = nm.getClass().getDeclaredField("methodNode");
                f.setAccessible(true);
                mn = (MethodNode) f.get(nm);
            } catch (NoSuchFieldException ignored) {
                try {
                    Field f2 = nm.getClass().getDeclaredField("node");
                    f2.setAccessible(true);
                    mn = (MethodNode) f2.get(nm);
                } catch (Exception ignored2) {
                }
            } catch (Exception ignored) {
            }

            if (mn == null || mn.instructions == null) {
                return tokens;
            }

            // Attempt to parse first object argument internal name from descriptor, e.g. (Lmr;I)V -> "mr"
            String firstArgType = parseFirstObjectParam(nm.normalizedDescriptor);

            // Sliding window to inspect context
            List<AbstractInsnNode> insns = new ArrayList<>();
            for (int i = 0; i < mn.instructions.size(); i++) {
                insns.add(mn.instructions.get(i));
            }

            for (int i = 0; i < insns.size(); i++) {
                AbstractInsnNode insn = insns.get(i);
                // Detect a call on the first argument: pattern ALOAD 1 followed immediately by invoke
                if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKESTATIC
                        || insn.getOpcode() == Opcodes.INVOKESPECIAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                    // Look back to see if ALOAD 1 (argument) was pushed right before
                    if (i >= 1) {
                        AbstractInsnNode prev = insns.get(i - 1);
                        if (prev.getOpcode() == Opcodes.ALOAD && prev instanceof VarInsnNode) {
                            VarInsnNode v = (VarInsnNode) prev;
                            if (v.var == 1) { // first argument (non-static)
                                MethodInsnNode call = (MethodInsnNode) insn;
                                tokens.add("CALL_ON_ARG:" + call.owner + "." + call.name);
                            }
                        }
                    }
                }

                // Field access on argument: ALOAD 1, GETFIELD owner.name
                if (insn.getOpcode() == Opcodes.GETFIELD && insn instanceof FieldInsnNode) {
                    // Backtrack to see if object was argument (simple heuristic)
                    if (i >= 1) {
                        AbstractInsnNode prev = insns.get(i - 1);
                        if (prev.getOpcode() == Opcodes.ALOAD && prev instanceof VarInsnNode) {
                            VarInsnNode v = (VarInsnNode) prev;
                            if (v.var == 1 && insn instanceof FieldInsnNode) {
                                FieldInsnNode fin = (FieldInsnNode) insn;
                                tokens.add("ARG_GETFIELD:" + fin.owner + "." + fin.name);
                            }
                        }
                    }
                }

                // Argument field accumulation/multiplication: detect PUTFIELD to argument with preceding IMUL
                if (insn.getOpcode() == Opcodes.PUTFIELD && insn instanceof FieldInsnNode) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    // check if it's writing to a field of argument (we see ALOAD1 earlier)
                    boolean writingArgField = false;
                    // naive: scan backwards a few insns for ALOAD 1
                    for (int j = Math.max(0, i - 4); j < i; j++) {
                        AbstractInsnNode a = insns.get(j);
                        if (a.getOpcode() == Opcodes.ALOAD && a instanceof VarInsnNode) {
                            VarInsnNode vn = (VarInsnNode) a;
                            if (vn.var == 1) {
                                writingArgField = true;
                                break;
                            }
                        }
                    }
                    if (writingArgField) {
                        // If there's IMUL shortly before, consider it a scaled assignment
                        boolean hasMul = false;
                        for (int j = Math.max(0, i - 3); j < i; j++) {
                            if (insns.get(j).getOpcode() == Opcodes.IMUL) {
                                hasMul = true;
                                break;
                            }
                        }
                        if (hasMul) {
                            tokens.add("ARG_ACCUM_MUL:" + fin.owner + "." + fin.name);
                        } else {
                            tokens.add("ARG_PUTFIELD:" + fin.owner + "." + fin.name);
                        }
                    }
                }

                // Zeroing detection on argument fields: ICONST_0 + PUTFIELD after ALOAD1
                if (insn.getOpcode() == Opcodes.PUTFIELD && insn instanceof FieldInsnNode) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    boolean pattern = false;
                    if (i >= 2) {
                        AbstractInsnNode before = insns.get(i - 1);
                        AbstractInsnNode before2 = insns.get(i - 2);
                        if (before.getOpcode() == Opcodes.ICONST_0 &&
                                before2.getOpcode() == Opcodes.ALOAD && before2 instanceof VarInsnNode) {
                            VarInsnNode vn = (VarInsnNode) before2;
                            if (vn.var == 1) {
                                pattern = true;
                            }
                        }
                    }
                    if (pattern) {
                        tokens.add("ARG_ZERO_FIELD:" + fin.owner + "." + fin.name);
                    }
                }

                // This accumulation detection: this.field += ... pattern
                if (insn.getOpcode() == Opcodes.PUTFIELD && insn instanceof FieldInsnNode) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    // Look for pattern: ALOAD 0, GETFIELD this.field, ..., IADD, PUTFIELD this.field
                    if (i >= 3) {
                        AbstractInsnNode maybeIadd = insns.get(i - 1);
                        AbstractInsnNode maybeGetfield = insns.get(i - 3);
                        AbstractInsnNode maybeAload0 = insns.get(i - 4 < 0 ? 0 : i - 4);
                        if (maybeIadd != null && maybeGetfield instanceof FieldInsnNode
                                && maybeAload0 != null && maybeAload0.getOpcode() == Opcodes.ALOAD) {
                            // Simplified heuristic: if PUTFIELD is to same field as earlier GETFIELD, treat as accumulation
                            FieldInsnNode priorField = (FieldInsnNode) maybeGetfield;
                            if (priorField.owner.equals(fin.owner) && priorField.name.equals(fin.name)) {
                                tokens.add("THIS_ACCUM_ADD:" + fin.owner + "." + fin.name);
                            }
                        }
                    }
                }
            }

            return tokens;
        });
    }

    /**
     * Parse first object parameter type internal name from a descriptor like "(Lmr;I)V" -> "mr"
     */
    private static String parseFirstObjectParam(String descriptor) {
        // crude parser: find first 'L' inside parentheses
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')');
        if (start < 0 || end < 0 || end <= start + 1) return "";
        String inside = descriptor.substring(start + 1, end);
        if (inside.startsWith("L")) {
            int semicolon = inside.indexOf(';');
            if (semicolon > 1) {
                return inside.substring(1, semicolon); // without L and ;
            }
        }
        return "";
    }

    private static class CorpusStats {
        final Map<String, Double> invokedIdf;
        final Map<String, Double> stringIdf;

        CorpusStats(Map<String, Double> invokedIdf, Map<String, Double> stringIdf) {
            this.invokedIdf = invokedIdf;
            this.stringIdf = stringIdf;
        }
    }

    private static boolean compatibleObjPrimPattern(String descA, String descB) {
        Type[] a = Type.getArgumentTypes(descA);
        Type[] b = Type.getArgumentTypes(descB);
        if (a.length != b.length) return false;

        for (int i = 0; i < a.length; i++) {
            if (!compatibleArg(a[i], b[i])) return false;
        }
        return true;
    }

    private static boolean compatibleArg(Type tA, Type tB) {
        // Array handling: both must be arrays with same dimensions and compatible element types.
        if (tA.getSort() == Type.ARRAY || tB.getSort() == Type.ARRAY) {
            if (tA.getSort() != Type.ARRAY || tB.getSort() != Type.ARRAY) {
                return false; // one is array, other is not
            }
            if (tA.getDimensions() != tB.getDimensions()) {
                return false; // different array depth
            }
            return compatibleArg(tA.getElementType(), tB.getElementType()); // recurse on base type
        }

        boolean objA = tA.getSort() == Type.OBJECT;
        boolean objB = tB.getSort() == Type.OBJECT;

        if (objA && objB) {
            // both are objects: require equal name length
            String nameA = tA.getInternalName(); // e.g., "java/lang/String"
            String nameB = tB.getInternalName();
            return nameA.length() == nameB.length();
        }

        if (objA != objB) {
            return false; // one is object, other is primitive
        }

        // both primitives
        return true;
    }
}
