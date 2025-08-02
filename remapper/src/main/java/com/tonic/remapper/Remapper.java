package com.tonic.remapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tonic.optionsparser.RemapperOptions;
import com.tonic.remapper.classes.ClassMatcher;
import com.tonic.remapper.dto.JClass;
import com.tonic.remapper.dto.JField;
import com.tonic.remapper.dto.JMethod;
import com.tonic.remapper.fields.FieldKey;
import com.tonic.remapper.fields.FieldMatcher;
import com.tonic.remapper.fields.FieldUsage;
import com.tonic.remapper.garbage.OpaquePredicateValueCollector;
import com.tonic.remapper.ui.MappingEditor;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import com.tonic.remapper.methods.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Remapper {
    private static final List<ClassNode> oldClasses = new ArrayList<>();
    private static final List<ClassNode> newClasses = new ArrayList<>();
    public static void main(String[] args) throws Exception
    {
        RemapperOptions parser = new RemapperOptions();
        parser.parse(args);

        if(parser.isEditor())
        {
            MappingEditor.main(null);
            return;
        }

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
        Map<MethodKey, MethodKey> seedMap = getSeedMap(candidates);

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

        // 8.  Build field-usage maps (which methods touch which fields)
        System.out.println("Building field-usage maps…");
        Map<FieldKey, Set<MethodKey>> oldFieldUses = FieldUsage.build(oldClasses);
        Map<FieldKey, Set<MethodKey>> newFieldUses = FieldUsage.build(newClasses);

        // 9.  Match fields (type-aware, class-aware, method-usage-aware)
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

        // 10.  Pick the best candidate per old field
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

        // 11.  Show the result
        System.out.println("Field mapping complete.  Found " + bestFieldMap.size() + " mappings.");
        System.out.println("Field mapping results (old -> new) with scores:");
        bestFieldMap.entrySet()
                .stream()
                .sorted(Comparator.comparingDouble(e -> -bestFieldScore.get(e.getKey())))
                .forEach(e -> System.out.printf("%s -> %s (score=%.3f)%n",
                        e.getKey(), e.getValue(), bestFieldScore.get(e.getKey())));

        String oldMappings = parser.getOldMappings();
        String outFile = parser.getNewMapping();
        if(oldMappings == null && outFile == null) {
            System.out.println("No output file specified, skipping remapping.");
            return;
        }

        if(oldMappings == null || outFile == null) {
            System.out.println("Either old mappings or new mapping output file is not specified, cannot remap.");
            parser.help();
            return;
        }

        // 12.  Remap mappings
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();

        Map<String,JClass> remappedClasses = buildDtoClassesForNewJar(newClasses, newMethods, newFieldNodesAll);
        try (Reader r = new InputStreamReader(new FileInputStream(oldMappings), StandardCharsets.UTF_8)) {
            JClass[] dtoClasses = gson.fromJson(r, JClass[].class);

            for (JClass oldCls : dtoClasses) {

                /* ---------- find the equivalent class in the NEW jar ---------- */
                ClassMatcher.ClassMatch cMatch = classMatchByOldOwner.get(oldCls.getObfuscatedName());
                if (cMatch == null) continue;                                 // class vanished / no match

                String newOwnerObf = cMatch.newFp.internalName;               // obfuscated internal name
                JClass newClsDto   = remappedClasses.get(newOwnerObf);
                if (newClsDto == null) continue;

                /* ---------- class name ---------- */
                if (oldCls.getName() != null && !oldCls.getName().isBlank()) {
                    newClsDto.setName(oldCls.getName());
                }

                /* build quick look-ups inside the target class */
                Map<String, JMethod> newMethodsBySig = newClsDto.getMethods()
                        .stream()
                        .collect(Collectors.toMap(
                                m -> m.getObfuscatedName() + m.getDescriptor(),
                                m -> m,
                                (a, b) -> a));                                 // ignore dups

                Map<String, JField>  newFieldsBySig  = newClsDto.getFields()
                        .stream()
                        .collect(Collectors.toMap(
                                f -> f.getObfuscatedName() + " " + f.getDescriptor(),
                                f -> f,
                                (a, b) -> a));

                /* ---------- methods ---------- */
                for (JMethod oldM : oldCls.getMethods()) {
                    if (oldM.getName() == null || oldM.getName().isBlank()) continue;

                    MethodKey oldKey = new MethodKey(
                            oldCls.getObfuscatedName(),
                            oldM.getObfuscatedName(),
                            oldM.getDescriptor());

                    MethodKey newKey = refined.get(oldKey);                    // match produced earlier
                    if (newKey == null) continue;

                    JMethod target = newMethodsBySig.get(newKey.name + newKey.desc);
                    if (target == null) continue;

                    target.setName(oldM.getName());
                    target.setOwner(newClsDto.getName());
                }

                /* ---------- fields ---------- */
                for (JField oldF : oldCls.getFields()) {
                    if (oldF.getName() == null || oldF.getName().isBlank()) continue;

                    FieldKey oldFKey = new FieldKey(
                            oldCls.getObfuscatedName(),
                            oldF.getObfuscatedName(),
                            oldF.getDescriptor());

                    FieldKey newFKey = bestFieldMap.get(oldFKey);              // match produced earlier
                    if (newFKey == null) continue;

                    JField target = newFieldsBySig.get(newFKey.name + " " + newFKey.desc);
                    if (target == null) continue;

                    target.setName(oldF.getName());
                    target.setOwner(newClsDto.getName());
                }
            }
        }

        // 13.  Write remapped dto to file
        List<JClass> remapped = new ArrayList<>(remappedClasses.values());
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            gson.toJson(remapped, w);
            System.out.println("Remapped dto written to " + outFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @NotNull
    private static Map<MethodKey, MethodKey> getSeedMap(List<MethodMatcher.Match> candidates) {
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
        return seedMap;
    }

    /**
     * Builds <obfuscated-class-name , JClass> from the raw ASM model we collected
     * for the *new* jar.
     *
     * @param classes  every ClassNode that came out of the new jar
     * @param methods  only the *used* MethodNodes (keyed by MethodKey) – what
     *                 loadMethodsFromJar(...) returns
     * @param fields   all FieldNodes (keyed by FieldKey) – what loadFieldsFromJar(...) returns
     *
     * @return a LinkedHashMap in the same iteration order as {@code classes}
     */
    public static Map<String, JClass> buildDtoClassesForNewJar(
            List<ClassNode> classes,
            Map<MethodKey, MethodNode> methods,
            Map<FieldKey, FieldNode> fields) {

        Map<String, JClass> dtoByClass = new LinkedHashMap<>();

        /* ---------- create one JClass for every ClassNode ---------- */
        for (ClassNode cn : classes) {
            JClass jc = new JClass();
            jc.setObfuscatedName(cn.name);
            dtoByClass.put(cn.name, jc);
        }

        /* ---------- add methods (only the used ones we kept) ---------- */
        for (Map.Entry<MethodKey, MethodNode> e : methods.entrySet()) {
            MethodKey  key = e.getKey();
            MethodNode mn  = e.getValue();

            JClass owner = dtoByClass.get(key.owner);
            if (owner == null) continue;

            JMethod jm = new JMethod();
            jm.setObfuscatedName(mn.name);
            jm.setDescriptor(mn.desc);
            jm.setOwnerObfuscatedName(key.owner);
            jm.setOwner(owner.getName());
            jm.setStatic((mn.access & Opcodes.ACC_STATIC) != 0);

            owner.getMethods().add(jm);
        }

        /* ---------- add *all* fields ---------- */
        for (Map.Entry<FieldKey, FieldNode> e : fields.entrySet()) {
            FieldKey  key = e.getKey();
            FieldNode fn  = e.getValue();

            JClass owner = dtoByClass.get(key.owner);
            if (owner == null) continue;

            JField jf = new JField();
            jf.setObfuscatedName(fn.name);
            jf.setDescriptor(fn.desc);
            jf.setOwnerObfuscatedName(key.owner);
            jf.setOwner(owner.getName());
            jf.setStatic((fn.access & Opcodes.ACC_STATIC) != 0);

            owner.getFields().add(jf);
        }

        return dtoByClass;
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

        var used = UsedMethodScanner.findUsedMethods(classes);
        // Filter methods to only those that are used
        methods.entrySet().removeIf(entry -> !used.contains(entry.getKey()));
        return methods;
    }
}
