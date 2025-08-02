package com.tonic.remap;

import com.tonic.util.optionsparser.RemapperOptions;
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

public class Remap {
    private static final List<ClassNode> oldClasses = new ArrayList<>();
    private static final List<ClassNode> newClasses = new ArrayList<>();
    public static void main(String[] args) throws Exception
    {
        RemapperOptions parser = new RemapperOptions();
        parser.parse(args);
        if(parser.getOldJar() == null || parser.getNewJar() == null) {
            parser.help();
            System.exit(0);
        }
        // 1. Load all methods from both jars
        Path oldJar = Paths.get(parser.getOldJar());
        Path newJar = Paths.get(parser.getNewJar());
        Map<MethodKey, MethodNode> newMethods = loadMethodsFromJar(newJar, newClasses);
        Map<MethodKey, MethodNode> oldMethods = loadMethodsFromJar(oldJar, oldClasses);

        List<ClassMatcher.ClassMatch> topMatches = ClassMatcher.matchClassesTopK(oldClasses, newClasses, 1, 0.3);
        Map<String, ClassMatcher.ClassMatch> classMatchByOldOwner = topMatches.stream()
                .collect(Collectors.toMap(
                        cm -> cm.oldFp.internalName,
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
                0.3, // neighbor weight
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

        // -----------------------------------------------------------------------------
// 8.  Build field-usage maps (which methods touch which fields)
// -----------------------------------------------------------------------------
        System.out.println("Building field-usage maps…");
        Map<FieldKey, Set<MethodKey>> oldFieldUses = FieldUsage.build(oldClasses);
        Map<FieldKey, Set<MethodKey>> newFieldUses = FieldUsage.build(newClasses);

// -----------------------------------------------------------------------------
// 9.  Match fields (type-aware, class-aware, method-usage-aware)
// -----------------------------------------------------------------------------
        System.out.println("Matching fields…");
        List<FieldMatcher.Match> fieldMatches =
                FieldMatcher.matchAll(
                        oldFieldNodesAll,
                        newFieldNodesAll,
                        oldFieldUses,
                        newFieldUses,
                        refined,
                        classMatchByOldOwner,
                        10
                );

// -----------------------------------------------------------------------------
// 10.  Pick the best candidate per old field
// -----------------------------------------------------------------------------
        Map<FieldKey, FieldKey> bestFieldMap = new HashMap<>();
        Map<FieldKey, Double>   bestFieldScore = new HashMap<>();
        double FIELD_THRESHOLD = 0.25;           // tweak
        for (FieldMatcher.Match m : fieldMatches) {
            if (m.score < FIELD_THRESHOLD) continue;
            Double prev = bestFieldScore.get(m.oldKey);
            if (prev == null || m.score > prev) {
                bestFieldMap.put(m.oldKey, m.newKey);
                bestFieldScore.put(m.oldKey, m.score);
            }
        }

// -----------------------------------------------------------------------------
// 11.  Show the result
// -----------------------------------------------------------------------------
        System.out.println("Field mapping complete.  Found " + bestFieldMap.size() + " mappings.");
        System.out.println("Field mapping results (old -> new) with scores:");
        bestFieldMap.entrySet()
                .stream()
                .sorted(Comparator.comparingDouble(e -> -bestFieldScore.get(e.getKey())))
                .forEach(e -> System.out.printf("%s -> %s (score=%.3f)%n",
                        e.getKey(), e.getValue(), bestFieldScore.get(e.getKey())));
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
}
