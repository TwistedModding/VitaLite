package com.tonic.remapper.fields;

import com.tonic.remapper.classes.ClassMatch;
import com.tonic.remapper.methods.MethodKey;
import com.tonic.remapper.misc.ProgressBar;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import java.util.HashMap;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Optimized field matcher with parallel processing and progress tracking.
 * Uses multi-threading to speed up the O(n*m) matching process.
 */
public class FieldMatcher {

    public static final class Match {
        public final FieldKey oldKey;
        public final FieldKey newKey;
        public final double score;
        public final Map<String, Double> scoreBreakdown;

        Match(FieldKey o, FieldKey n, double s, Map<String, Double> breakdown) {
            oldKey = o;
            newKey = n;
            score = s;
            scoreBreakdown = breakdown;
        }

        @Override
        public String toString() {
            return oldKey + " -> " + newKey + " : " + String.format("%.3f", score);
        }
    }

    public static List<Match> matchAll(
            Map<FieldKey, FieldNode> oldFields,
            Map<FieldKey, FieldNode> newFields,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey> methodMap,
            Map<String, ClassMatch> classMatchByOldOwner,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> oldProfiles,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> newProfiles,
            int topKPerOld
    ) {
        if (oldFields.isEmpty() || newFields.isEmpty()) return List.of();

        System.out.println("Pre-computing field analysis data...");

        // ADD THIS: Create class name mapping for descriptor remapping
        Map<String, String> oldToNewClassMap = DescriptorRemapper.createClassMapping(classMatchByOldOwner);
        System.out.println("Class mapping size: " + oldToNewClassMap.size());

        // Pre-compute all the shared data structures (these are read-only during matching)
        Map<FieldKey, Set<FieldKey>> oldCoOccur = computeCoOccurrences(oldUses);
        Map<FieldKey, Set<FieldKey>> newCoOccur = computeCoOccurrences(newUses);
        Map<FieldKey, Integer> oldFieldIndices = computeFieldIndices(oldFields);
        Map<FieldKey, Integer> newFieldIndices = computeFieldIndices(newFields);
        Map<String, List<FieldKey>> oldByOwner = groupByOwner(oldFields.keySet());
        Map<String, List<FieldKey>> newByOwner = groupByOwner(newFields.keySet());

        // Create thread-safe collection for results
        List<Match> collected = Collections.synchronizedList(new ArrayList<>());

        // Progress tracking
        AtomicLong processedCount = new AtomicLong(0);
        long totalFields = oldFields.size();

        // Determine optimal thread count (use available processors, but cap at 8 for memory reasons)
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        System.out.println("Matching " + totalFields + " old fields against " +
                newFields.size() + " new fields using " + threadCount + " threads...");
        ProgressBar progressBar = new ProgressBar(totalFields, 50);

        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<List<Match>>> futures = new ArrayList<>();

        // Convert to list for easier partitioning
        List<Map.Entry<FieldKey, FieldNode>> oldFieldsList = new ArrayList<>(oldFields.entrySet());

        // Calculate chunk size for work distribution
        int chunkSize = Math.max(1, (oldFieldsList.size() + threadCount - 1) / threadCount);

        // Submit tasks for parallel processing
        for (int i = 0; i < oldFieldsList.size(); i += chunkSize) {
            final int startIdx = i;
            final int endIdx = Math.min(i + chunkSize, oldFieldsList.size());

            Future<List<Match>> future = executor.submit(() -> {
                List<Match> localMatches = new ArrayList<>();

                for (int idx = startIdx; idx < endIdx; idx++) {
                    Map.Entry<FieldKey, FieldNode> entry = oldFieldsList.get(idx);
                    FieldKey oldKey = entry.getKey();
                    FieldNode oldFn = entry.getValue();

                    // Process this old field against all new fields
                    List<Match> fieldMatches = matchSingleOldField(
                            oldKey, oldFn, newFields, oldUses, newUses, methodMap,
                            classMatchByOldOwner, oldProfiles, newProfiles,
                            oldCoOccur, newCoOccur, oldFieldIndices, newFieldIndices,
                            oldByOwner, newByOwner, oldToNewClassMap, topKPerOld  // ADD THIS PARAMETER
                    );

                    localMatches.addAll(fieldMatches);

                    // Update progress
                    long processed = processedCount.incrementAndGet();
                    progressBar.update(processed);
                }

                return localMatches;
            });

            futures.add(future);
        }

        // Collect results from all threads
        try {
            for (Future<List<Match>> future : futures) {
                collected.addAll(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException("Field matching failed", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        // Ensure progress bar shows 100%
        progressBar.update(totalFields);

        // Sort results by score (descending)
        collected.sort(Comparator.comparingDouble(m -> -m.score));

        // Debug: Show top matches if no good matches found
        if (collected.isEmpty() || collected.stream().noneMatch(m -> m.score > 0.25)) {
            System.out.println("WARNING: Few or no good field matches found!");
            System.out.println("Top 5 match scores:");
            collected.stream().limit(5).forEach(m ->
                    System.out.printf("  %s -> %s: %.3f (type=%.2f, usage=%.2f, owner=%.2f)\n",
                            m.oldKey, m.newKey, m.score,
                            m.scoreBreakdown.get("type"),
                            m.scoreBreakdown.get("usage"),
                            m.scoreBreakdown.get("owner")));
        }

        System.out.println("Field matching complete. Generated " + collected.size() + " candidates.");
        return collected;
    }

    /**
     * Match a single old field against all new fields.
     * This method is thread-safe as it only reads shared data and returns local results.
     */
    private static List<Match> matchSingleOldField(
            FieldKey oldKey,
            FieldNode oldFn,
            Map<FieldKey, FieldNode> newFields,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey> methodMap,
            Map<String, ClassMatch> classMatchByOldOwner,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> oldProfiles,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> newProfiles,
            Map<FieldKey, Set<FieldKey>> oldCoOccur,
            Map<FieldKey, Set<FieldKey>> newCoOccur,
            Map<FieldKey, Integer> oldFieldIndices,
            Map<FieldKey, Integer> newFieldIndices,
            Map<String, List<FieldKey>> oldByOwner,
            Map<String, List<FieldKey>> newByOwner,
            Map<String, String> oldToNewClassMap,  // ADD THIS PARAMETER
            int topKPerOld
    ) {
        boolean oldStatic = (oldFn.access & Opcodes.ACC_STATIC) != 0;
        boolean oldFinal = (oldFn.access & Opcodes.ACC_FINAL) != 0;

        // Get class match info
        ClassMatch classMatch = classMatchByOldOwner.get(oldKey.owner);
        double classConfidence = classMatch != null ? classMatch.similarity : 0.0;
        String likelyNewOwner = classMatch != null ? classMatch.newFp.internalName : null;

        // Use a priority queue to keep only top-K matches (min-heap: smallest score at head)
        PriorityQueue<Match> topK = new PriorityQueue<>(topKPerOld, Comparator.comparingDouble(m -> m.score));

        for (Map.Entry<FieldKey, FieldNode> en : newFields.entrySet()) {
            FieldKey newKey = en.getKey();
            FieldNode newFn = en.getValue();
            boolean newStatic = (newFn.access & Opcodes.ACC_STATIC) != 0;
            boolean newFinal = (newFn.access & Opcodes.ACC_FINAL) != 0;

            Map<String, Double> breakdown = new HashMap<>();

            // Type compatibility
            Double typeScore = computeTypeCompatibility(oldKey.desc, newKey.desc, oldFinal, newFinal, oldToNewClassMap);
            if (typeScore == null) continue;
            breakdown.put("type", typeScore);

            // Modifier agreement
            double modifierScore = 0.0;
            if (oldStatic == newStatic) modifierScore += 1.0;
            if (oldFinal == newFinal) modifierScore += 0.5;
            modifierScore /= 1.5;
            breakdown.put("modifiers", modifierScore);

            // Owner class mapping
            double ownerScore = 0.0;
            if (likelyNewOwner != null && likelyNewOwner.equals(newKey.owner)) {
                if (!oldStatic && !newStatic) {
                    ownerScore = 1.0 * classConfidence;
                } else {
                    ownerScore = 0.6 * classConfidence;
                }
            } else if (!oldStatic && !newStatic) {
                ownerScore = -0.2;
            }
            breakdown.put("owner", ownerScore);

            // Method usage overlap
            Set<MethodKey> oldU = oldUses.getOrDefault(oldKey, Set.of());
            Set<MethodKey> newU = newUses.getOrDefault(newKey, Set.of());

            Set<MethodKey> translatedOldU = oldU.stream()
                    .map(methodMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            double usageScore = 0.0;
            if (!translatedOldU.isEmpty() || !newU.isEmpty()) {
                usageScore = jaccard(translatedOldU, newU);
                if (usageScore > 0.5 && oldU.size() == newU.size()) {
                    usageScore = Math.min(1.0, usageScore + 0.1);
                }
            }
            breakdown.put("usage", usageScore);

            // Co-occurrence analysis
            double coOccurScore = computeCoOccurrenceScore(
                    oldKey, newKey, oldCoOccur, newCoOccur,
                    newFields.keySet(), FieldMatcher::fieldKeyMatch
            );
            breakdown.put("cooccur", coOccurScore);

            // Field proximity
            double proximityScore = 0.0;
            if (likelyNewOwner != null && likelyNewOwner.equals(newKey.owner)) {
                List<FieldKey> oldClassFields = oldByOwner.get(oldKey.owner);
                List<FieldKey> newClassFields = newByOwner.get(newKey.owner);
                if (oldClassFields != null && newClassFields != null) {
                    proximityScore = computeProximityScore(
                            oldKey, newKey, oldFieldIndices, newFieldIndices,
                            oldClassFields.size(), newClassFields.size()
                    );
                }
            }
            breakdown.put("proximity", proximityScore);

            // Primitive patterns
            double patternScore = 0.0;
            if (isPrimitive(oldKey.desc) && isPrimitive(newKey.desc)) {
                patternScore = computePrimitivePatternScore(
                        oldKey, newKey, oldUses, newUses, methodMap
                );
            }
            breakdown.put("pattern", patternScore);

            // Profile similarity
            double profileScore = 0.0;
            if (oldProfiles != null && newProfiles != null) {
                FieldAccessAnalyzer.FieldAccessProfile oldProfile = oldProfiles.get(oldKey);
                FieldAccessAnalyzer.FieldAccessProfile newProfile = newProfiles.get(newKey);

                if (oldProfile != null && newProfile != null) {
                    profileScore = oldProfile.similarity(newProfile);

                    if (isPrimitive(oldKey.desc)) {
                        if (oldProfile.isLikelyCounter && newProfile.isLikelyCounter) {
                            profileScore = Math.min(1.0, profileScore + 0.3);
                        } else if (oldProfile.isLikelyFlag && newProfile.isLikelyFlag) {
                            profileScore = Math.min(1.0, profileScore + 0.3);
                        } else if (oldProfile.isLikelyIndex && newProfile.isLikelyIndex) {
                            profileScore = Math.min(1.0, profileScore + 0.25);
                        } else if (oldProfile.isLikelyConstant && newProfile.isLikelyConstant) {
                            profileScore = Math.min(1.0, profileScore + 0.2);
                        }

                        if ((oldProfile.isLikelyCounter && !newProfile.isLikelyCounter) ||
                                (!oldProfile.isLikelyCounter && newProfile.isLikelyCounter)) {
                            profileScore *= 0.5;
                        }
                    }
                }
            }
            breakdown.put("profile", profileScore);

            // Compute weighted final score
            double finalScore = computeWeightedScore(breakdown, oldStatic, isPrimitive(oldKey.desc));
            double hashTieBreaker = ((oldKey.hashCode() ^ newKey.hashCode()) & 0xFFFF) / 1000000.0;
            finalScore += hashTieBreaker;

            Match m = new Match(oldKey, newKey, finalScore, breakdown);

            // Maintain top-K matches (min-heap: remove smallest when full)
            if (topK.size() < topKPerOld) {
                topK.offer(m);
            } else if (finalScore > topK.peek().score) {  // If this score is better than the worst in top-K
                topK.poll();  // Remove the worst (smallest score)
                topK.offer(m);  // Add the better match
            }
        }

        return new ArrayList<>(topK);
    }

    // Simplified co-occurrence score (removed unused parameters)
    private static double computeCoOccurrenceScore(
            FieldKey oldField, FieldKey newField,
            Map<FieldKey, Set<FieldKey>> oldCoOccur,
            Map<FieldKey, Set<FieldKey>> newCoOccur,
            Set<FieldKey> newFields,
            BiPredicate<FieldKey, FieldKey> matcher) {

        Set<FieldKey> oldCo = oldCoOccur.getOrDefault(oldField, Set.of());
        Set<FieldKey> newCo = newCoOccur.getOrDefault(newField, Set.of());

        if (oldCo.isEmpty() && newCo.isEmpty()) return 0.5;
        if (oldCo.isEmpty() || newCo.isEmpty()) return 0.0;

        int matches = 0;
        for (FieldKey o : oldCo) {
            for (FieldKey n : newCo) {
                if (matcher.test(o, n)) {
                    matches++;
                    break;
                }
            }
        }

        return (double) matches / Math.max(oldCo.size(), newCo.size());
    }

    // ========== All the existing helper methods remain the same ==========

    private static double computeWeightedScore(Map<String, Double> breakdown,
                                               boolean isStatic, boolean isPrimitive) {
        double score = 0.0;

        if (isPrimitive) {
            score += breakdown.getOrDefault("type", 0.0) * 0.10;
            score += breakdown.getOrDefault("modifiers", 0.0) * 0.05;
            score += breakdown.getOrDefault("owner", 0.0) * 0.20;
            score += breakdown.getOrDefault("usage", 0.0) * 0.30;
            score += breakdown.getOrDefault("cooccur", 0.0) * 0.08;
            score += breakdown.getOrDefault("proximity", 0.0) * 0.05;
            score += breakdown.getOrDefault("pattern", 0.0) * 0.07;
            score += breakdown.getOrDefault("profile", 0.0) * 0.15;
        } else if (isStatic) {
            score += breakdown.getOrDefault("type", 0.0) * 0.20;
            score += breakdown.getOrDefault("modifiers", 0.0) * 0.10;
            score += breakdown.getOrDefault("owner", 0.0) * 0.15;
            score += breakdown.getOrDefault("usage", 0.0) * 0.35;
            score += breakdown.getOrDefault("cooccur", 0.0) * 0.10;
            score += breakdown.getOrDefault("profile", 0.0) * 0.10;
        } else {
            score += breakdown.getOrDefault("type", 0.0) * 0.18;
            score += breakdown.getOrDefault("modifiers", 0.0) * 0.05;
            score += breakdown.getOrDefault("owner", 0.0) * 0.28;
            score += breakdown.getOrDefault("usage", 0.0) * 0.27;
            score += breakdown.getOrDefault("cooccur", 0.0) * 0.10;
            score += breakdown.getOrDefault("proximity", 0.0) * 0.05;
            score += breakdown.getOrDefault("profile", 0.0) * 0.07;
        }

        return score;
    }

    private static Double computeTypeCompatibility(String oldDesc, String newDesc,
                                                   boolean oldFinal, boolean newFinal) {
        // Call the new version with empty map for backward compatibility
        return computeTypeCompatibility(oldDesc, newDesc, oldFinal, newFinal, new HashMap<>());
    }

    private static Double computeTypeCompatibility(String oldDesc, String newDesc,
                                                   boolean oldFinal, boolean newFinal,
                                                   Map<String, String> oldToNewClassMap) {
        // Check if descriptors match after remapping
        if (DescriptorRemapper.descriptorsMatch(oldDesc, newDesc, oldToNewClassMap)) {
            // Perfect match after remapping
            if (isPrimitive(oldDesc)) {
                return 1.0;  // Primitives should always match exactly
            }
            // For objects, bonus if both final
            return (oldFinal && newFinal) ? 1.0 : 0.95;
        }

        Type oldType = Type.getType(oldDesc);
        Type newType = Type.getType(newDesc);

        // For primitives, use existing logic
        if (isPrimitive(oldDesc) && isPrimitive(newDesc)) {
            return getPrimitiveCompatibility(oldType, newType);
        }

        // For objects, check if they might be related even if remapping failed
        if (isObjectLike(oldType) && isObjectLike(newType)) {
            // If both are objects but remapping didn't match, they might still be related
            // This can happen if the class mapping is incomplete
            if (oldType.getSort() == Type.OBJECT && newType.getSort() == Type.OBJECT) {
                // Check if the old class has a mapping at all
                String oldClass = oldType.getInternalName();
                if (oldToNewClassMap.containsKey(oldClass)) {
                    // We have a mapping but it doesn't match - likely wrong field
                    return 0.2;  // Low score
                } else {
                    // No mapping found - could be a class that wasn't matched
                    return 0.4;  // Medium-low score
                }
            }

            if (oldType.getSort() == Type.ARRAY && newType.getSort() == Type.ARRAY) {
                if (oldType.getDimensions() == newType.getDimensions()) {
                    // Same dimension arrays - check element type
                    String oldElementDesc = oldType.getElementType().getDescriptor();
                    String newElementDesc = newType.getElementType().getDescriptor();

                    if (DescriptorRemapper.descriptorsMatch(oldElementDesc, newElementDesc, oldToNewClassMap)) {
                        return 0.9;  // Array types match after remapping
                    }
                    return 0.3;  // Same dimensions but different element types
                }
                return 0.2;  // Different dimensions
            }
            return 0.2;  // Other object-like mismatch
        }

        return null;  // Incompatible types
    }

    private static Double getPrimitiveCompatibility(Type old, Type newT) {
        // Since primitives always stay the same type between versions,
        // we should strongly favor exact matches
        if (old.equals(newT)) return 1.0;  // Perfect match for same primitive type

        // Much lower scores for different primitive types
        // These should rarely match unless there's strong evidence from other signals
        int oldSort = old.getSort();
        int newSort = newT.getSort();

        // int <-> long (only if very strong usage evidence)
        if ((oldSort == Type.INT && newSort == Type.LONG) ||
                (oldSort == Type.LONG && newSort == Type.INT)) {
            return 0.05;  // Very low - would need strong other signals
        }

        // byte/short <-> int (very unlikely)
        if ((oldSort == Type.BYTE || oldSort == Type.SHORT) && newSort == Type.INT) {
            return 0.02;
        }

        // float <-> double (very unlikely)
        if ((oldSort == Type.FLOAT && newSort == Type.DOUBLE) ||
                (oldSort == Type.DOUBLE && newSort == Type.FLOAT)) {
            return 0.05;
        }

        // boolean <-> byte/int (extremely unlikely)
        if ((oldSort == Type.BOOLEAN && (newSort == Type.BYTE || newSort == Type.INT)) ||
                ((oldSort == Type.BYTE || oldSort == Type.INT) && newSort == Type.BOOLEAN)) {
            return 0.01;
        }

        // Different primitives - basically incompatible
        return 0.001;  // Near zero - would need overwhelming evidence from other signals
    }

    private static Map<FieldKey, Set<FieldKey>> computeCoOccurrences(
            Map<FieldKey, Set<MethodKey>> fieldUses) {
        Map<FieldKey, Set<FieldKey>> coOccur = new HashMap<>();

        Map<MethodKey, Set<FieldKey>> methodToFields = new HashMap<>();
        for (Map.Entry<FieldKey, Set<MethodKey>> e : fieldUses.entrySet()) {
            for (MethodKey m : e.getValue()) {
                methodToFields.computeIfAbsent(m, k -> new HashSet<>()).add(e.getKey());
            }
        }

        for (Set<FieldKey> fields : methodToFields.values()) {
            for (FieldKey f1 : fields) {
                for (FieldKey f2 : fields) {
                    if (!f1.equals(f2)) {
                        coOccur.computeIfAbsent(f1, k -> new HashSet<>()).add(f2);
                    }
                }
            }
        }

        return coOccur;
    }

    private static boolean fieldKeyMatch(FieldKey old, FieldKey newF) {
        return old.desc.equals(newF.desc);
    }

    @FunctionalInterface
    private interface BiPredicate<T, U> {
        boolean test(T t, U u);
    }

    private static Map<FieldKey, Integer> computeFieldIndices(Map<FieldKey, FieldNode> fields) {
        Map<String, List<FieldKey>> byOwner = groupByOwner(fields.keySet());
        Map<FieldKey, Integer> indices = new HashMap<>();

        for (List<FieldKey> classFields : byOwner.values()) {
            for (int i = 0; i < classFields.size(); i++) {
                indices.put(classFields.get(i), i);
            }
        }

        return indices;
    }

    private static Map<String, List<FieldKey>> groupByOwner(Set<FieldKey> fields) {
        return fields.stream().collect(Collectors.groupingBy(
                f -> f.owner,
                Collectors.toList()
        ));
    }

    private static double computeProximityScore(
            FieldKey old, FieldKey newF,
            Map<FieldKey, Integer> oldIndices, Map<FieldKey, Integer> newIndices,
            int oldClassSize, int newClassSize) {

        Integer oldIdx = oldIndices.get(old);
        Integer newIdx = newIndices.get(newF);

        if (oldIdx == null || newIdx == null) return 0.0;

        double oldPos = (double) oldIdx / Math.max(1, oldClassSize - 1);
        double newPos = (double) newIdx / Math.max(1, newClassSize - 1);

        double distance = Math.abs(oldPos - newPos);
        return Math.max(0, 1.0 - distance * 2);
    }

    private static double computePrimitivePatternScore(
            FieldKey old, FieldKey newF,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey> methodMap) {

        Set<MethodKey> oldMethods = oldUses.getOrDefault(old, Set.of());
        Set<MethodKey> newMethods = newUses.getOrDefault(newF, Set.of());

        double usageRatio = 0.0;
        if (oldMethods.size() > 0 && newMethods.size() > 0) {
            int min = Math.min(oldMethods.size(), newMethods.size());
            int max = Math.max(oldMethods.size(), newMethods.size());
            usageRatio = (double) min / max;
        }

        return usageRatio * 0.5;
    }

    private static boolean isObjectLike(Type t) {
        return t.getSort() == Type.OBJECT ||
                (t.getSort() == Type.ARRAY && t.getElementType().getSort() == Type.OBJECT);
    }

    private static boolean isPrimitive(String desc) {
        Type t = Type.getType(desc);
        int sort = t.getSort();
        return sort >= Type.BOOLEAN && sort <= Type.DOUBLE;
    }

    private static <T> double jaccard(Set<T> x, Set<T> y) {
        if (x.isEmpty() && y.isEmpty()) return 1.0;
        Set<T> inter = new HashSet<>(x);
        inter.retainAll(y);
        Set<T> union = new HashSet<>(x);
        union.addAll(y);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }
}