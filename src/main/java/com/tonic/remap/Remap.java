package com.tonic.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Remap { //todo: write propper used method filter
    private static final List<ClassNode> oldClasses = new ArrayList<>();
    private static final List<ClassNode> newClasses = new ArrayList<>();
    public static void main(String[] args) throws Exception
    {
        // 1. Load all methods from both jars
        System.out.println("Loading methods from jars...");
        Path oldJar = Paths.get("C:/test/remap/230.jar");
        Path newJar = Paths.get("C:/test/remap/231.jar");
        Map<MethodKey, MethodNode> newMethods = loadMethodsFromJar(newJar, newClasses);
        Map<MethodKey, MethodNode> oldMethods = loadMethodsFromJar(oldJar, oldClasses);

        List<ClassMatcher.ClassMatch> topMatches = ClassMatcher.matchClassesTopK(oldClasses, newClasses, 1, 0.3);
        Map<String, ClassMatcher.ClassMatch> classMatchByOldOwner = topMatches.stream()
                .collect(Collectors.toMap(
                        cm -> cm.oldFp.internalName, // or .oldClass() / whatever accessor
                        cm -> cm
                ));

        // 2. Normalize
        System.out.println("Normalizing methods...");
        Map<MethodKey, NormalizedMethod> oldNorm = new HashMap<>();
        Map<MethodKey, NormalizedMethod> newNorm = new HashMap<>();
        for (Map.Entry<MethodKey, MethodNode> e : oldMethods.entrySet()) {
            oldNorm.put(e.getKey(), new NormalizedMethod(e.getKey().owner, e.getValue()));
        }
        for (Map.Entry<MethodKey, MethodNode> e : newMethods.entrySet()) {
            newNorm.put(e.getKey(), new NormalizedMethod(e.getKey().owner, e.getValue()));
        }

        // 3. Initial matching: top 5 candidates per old method
        System.out.println("Matching methods...");
        List<MethodMatcher.Match> candidates = MethodMatcher.matchAll(oldNorm, newNorm, classMatchByOldOwner, 50, 0.5);

        // 4. Seed best mapping (best score per old method)
        System.out.println("Building initial mapping...");
        Map<MethodKey, MethodKey> seedMap = new HashMap<>();
        Map<MethodKey, Double> bestScore = new HashMap<>();
        double SCORE_THRESHOLD = 0.1;
        for (MethodMatcher.Match m : candidates) {
            if (m.score < SCORE_THRESHOLD) {
                continue;
            }
            Double prev = bestScore.get(m.oldKey);
            if (prev == null || m.score > prev) {
                seedMap.put(m.oldKey, m.newKey);
                bestScore.put(m.oldKey, m.score);
            }
        }

        // 5. Build call graphs (restrict to provided set so edges are internal)
        System.out.println("Extracting call graphs...");
        Map<MethodKey, Set<MethodKey>> oldCG = CallGraphExtractor.extractCallGraph(oldMethods, true);
        Map<MethodKey, Set<MethodKey>> newCG = CallGraphExtractor.extractCallGraph(newMethods, true);

        // 6. Refine mapping
        System.out.println("Refining mapping using call graphs...");
        Map<MethodKey, MethodKey> refined = IterativeCallGraphRefiner.refine(
                candidates,
                oldCG,
                newCG,
                10, // max iterations
                0.3, // neighbor weight (tune: e.g., 0.3 means 30% neighborhood influence)
                seedMap
        );

        // Fetch opaques
        var opaquePredicates = OpaquePredicateValueCollector.collectMostFrequent(newMethods);

        // 7. Output results
        System.out.println("Mapping complete. Found " + refined.size() + " method mappings.");
        System.out.println("Final mapping (old -> new) with initial scores:");
        for (Map.Entry<MethodKey, MethodKey> e : refined.entrySet()) {
            MethodKey oldKey = e.getKey();
            MethodKey newKey = e.getValue();
            Integer opaque = opaquePredicates.get(newKey);
            double score = 0.0;
            // find the original match score if present
            for (MethodMatcher.Match m : candidates) {
                if (m.oldKey.equals(oldKey) && m.newKey.equals(newKey)) {
                    score = m.score;
                    break;
                }
            }
            System.out.printf("%s -> %s [%s] (score=%.3f)%n", oldKey, newKey, opaque, score);
        }

        // ---------------- Field remapping integration ----------------

        System.out.println("\n--- Starting field remapping integration ---");

        // Load all field nodes from jars
        Map<FieldKey, FieldNode> oldFieldNodesAll = loadFieldsFromJar(oldJar);
        Map<FieldKey, FieldNode> newFieldNodesAll = loadFieldsFromJar(newJar);

        // Extract field usage (readers/writers) from the filtered method sets
        FieldUsageExtractor.FieldUsage oldFieldUsage = FieldUsageExtractor.extractFieldUsage(oldMethods);
        FieldUsageExtractor.FieldUsage newFieldUsage = FieldUsageExtractor.extractFieldUsage(newMethods);

        // Build normalized fields limited to those actually used (read or written)
        Map<FieldKey, NormalizedField> oldFields = buildNormalizedFields(oldFieldUsage, oldFieldNodesAll);
        Map<FieldKey, NormalizedField> newFields = buildNormalizedFields(newFieldUsage, newFieldNodesAll);

        // Match fields using the refined method mapping as neighborhood context
        System.out.println("Matching fields...");
        List<FieldMatcher.Match> fieldCandidates = FieldMatcher.matchAll(
                oldFields,
                newFields,
                refined, // method mapping
                5,       // topK per old field
                0.5      // neighbor weight
        );

        // Seed best field mapping
        Map<FieldKey, FieldKey> seedFieldMap = new HashMap<>();
        Map<FieldKey, Double> bestFieldScore = new HashMap<>();
        double FIELD_SCORE_THRESHOLD = 0.1;
        for (FieldMatcher.Match m : fieldCandidates) {
            if (m.score < FIELD_SCORE_THRESHOLD) continue;
            Double prev = bestFieldScore.get(m.oldKey);
            if (prev == null || m.score > prev) {
                seedFieldMap.put(m.oldKey, m.newKey);
                bestFieldScore.put(m.oldKey, m.score);
            }
        }

        // Output field mappings
        System.out.println("Field mapping results (old -> new) with scores:");
        for (Map.Entry<FieldKey, FieldKey> e : seedFieldMap.entrySet()) {
            FieldKey oldKey = e.getKey();
            FieldKey newKey = e.getValue();
            double score = bestFieldScore.getOrDefault(oldKey, 0.0);
            System.out.printf("%s -> %s (score=%.3f)%n", oldKey, newKey, score);
        }
    }

    /**
     * Builds normalized fields from usage + raw field nodes.
     */
    private static Map<FieldKey, NormalizedField> buildNormalizedFields(
            FieldUsageExtractor.FieldUsage usage,
            Map<FieldKey, FieldNode> fieldNodeMap
    ) {
        Map<FieldKey, NormalizedField> normalized = new HashMap<>();
        Set<FieldKey> usedFields = new HashSet<>();
        usedFields.addAll(usage.readers.keySet());
        usedFields.addAll(usage.writers.keySet());

        for (FieldKey fk : usedFields) {
            FieldNode fn = fieldNodeMap.get(fk);
            if (fn == null) continue; // missing definition
            Set<MethodKey> readers = usage.readers.getOrDefault(fk, Collections.emptySet());
            Set<MethodKey> writers = usage.writers.getOrDefault(fk, Collections.emptySet());
            normalized.put(fk, new NormalizedField(fk.owner, fn, readers, writers));
        }
        return normalized;
    }

    /**
     * Loads all FieldNodes from a jar, returning a map from FieldKey to FieldNode.
     */
    public static Map<FieldKey, FieldNode> loadFieldsFromJar(Path jarPath) throws Exception {
        Map<FieldKey, FieldNode> fields = new HashMap<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || entry.isDirectory()) {
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);

                    String owner = cn.name;
                    List<FieldNode> fieldList = cn.fields;
                    for (FieldNode fn : fieldList) {
                        FieldKey key = new FieldKey(owner, fn.name, fn.desc);
                        fields.put(key, fn);
                    }
                }
            }
        }

        return fields;
    }

    /**
     * Loads all MethodNodes from a jar, returning a map from MethodKey to MethodNode.
     */
    public static Map<MethodKey, MethodNode> loadMethodsFromJar(Path jarPath, List<ClassNode> classes) throws Exception {
        Map<MethodKey, MethodNode> methods = new HashMap<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || entry.isDirectory()) {
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    classes.add(cn);

                    for(MethodNode mn : cn.methods) {
                        MethodKey key = new MethodKey(cn.name, mn.name, mn.desc);
                        methods.put(key, mn);
                    }
                }
            }
        }

        var used = UsedMethodScannerAsm.findUsedMethods(classes);
        // Filter methods to only those that are used
        methods.entrySet().removeIf(entry -> !used.contains(entry.getKey()));
        return methods;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> intersect = new HashSet<>(a);
        intersect.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersect.size() / union.size();
    }

    private static double overlapCoefficient(Set<?> a, Set<?> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<Object> smaller = a.size() < b.size() ? new HashSet<>(a) : new HashSet<>(b);
        Set<Object> larger = a.size() < b.size() ? new HashSet<>(b) : new HashSet<>(a);
        smaller.retainAll(larger);
        return (double) smaller.size() / Math.min(a.size(), b.size());
    }

    private static void dumpNormalized(String tag, MethodKey key, NormalizedMethod nm) {
        System.out.println("=== " + tag + " ===");
        System.out.println("Method: " + key);
        System.out.println("  normalizedDescriptor: " + nm.normalizedDescriptor);
        System.out.println("  invoked signatures: " + nm.invokedSignatures);
        System.out.println("  string constants: " + nm.stringConstants);
        System.out.println("  opcode histogram keys: " + nm.opcodeHistogram.keySet());
        System.out.println("====================");
    }
}
