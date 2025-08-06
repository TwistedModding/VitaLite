package com.tonic.remapper.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fingerprint for a class, capturing structural & implementation signals.
 * Optimized for OSRS/RuneLite obfuscation patterns with type reference analysis.
 */
public final class ClassFingerprint {
    public enum ClassType {
        CLASS,
        ABSTRACT_CLASS,
        INTERFACE,
        ENUM,
        ANNOTATION
    }

    public final String internalName;
    public final ClassType classType;
    public final Set<String> stableHierarchy; // Only non-obfuscated supers/interfaces
    public final int constructorCount;
    public final boolean hasStaticInitializer;
    public final Map<String, Integer> normalizedMethodDescriptors; // Object types replaced with placeholders
    public final Map<String, Integer> primitiveOnlyDescriptors; // Methods with only primitive params/returns
    public final Set<String> staticMethodDescriptors; // Normalized descriptors for static methods
    public final Map<String, Integer> opcodeHistogram;
    public final Set<String> stringConstants;
    public final Map<String, Integer> staticFieldTypes;
    public final Map<String, Integer> instanceFieldTypes;
    public final Set<String> externalMethodCalls; // Calls to java.*, javax.*, net.runelite.*, etc.
    public final Set<String> caughtExceptions; // Exception types caught
    public final Set<String> thrownExceptions; // Exception types in method signatures
    public final Map<String, Integer> annotationUsage; // Annotation types used
    public final Set<String> runeLiteEventSubscriptions; // RuneLite events this class subscribes to
    public final int innerClassCount;
    public final boolean hasSerialVersionUID;
    public final int totalMethodCount;
    public final int totalFieldCount;

    // Type reference tracking
    public final int fieldTypeReferenceCount;      // How many fields have this class as their type
    public final int methodParameterReferenceCount; // How many methods take this as parameter
    public final int methodReturnReferenceCount;    // How many methods return this type
    public final Map<String, Integer> referencingClasses; // Which classes reference this one
    public final Set<String> referencedTypes; // Types this class references (normalized)

    private ClassFingerprint(
            String internalName,
            ClassType classType,
            Set<String> stableHierarchy,
            int constructorCount,
            boolean hasStaticInitializer,
            Map<String, Integer> normalizedMethodDescriptors,
            Map<String, Integer> primitiveOnlyDescriptors,
            Set<String> staticMethodDescriptors,
            Map<String, Integer> opcodeHistogram,
            Set<String> stringConstants,
            Map<String, Integer> staticFieldTypes,
            Map<String, Integer> instanceFieldTypes,
            Set<String> externalMethodCalls,
            Set<String> caughtExceptions,
            Set<String> thrownExceptions,
            Map<String, Integer> annotationUsage,
            Set<String> runeLiteEventSubscriptions,
            int innerClassCount,
            boolean hasSerialVersionUID,
            int fieldTypeReferenceCount,
            int methodParameterReferenceCount,
            int methodReturnReferenceCount,
            Map<String, Integer> referencingClasses,
            Set<String> referencedTypes
    ) {
        this.internalName = internalName;
        this.classType = classType;
        this.stableHierarchy = Set.copyOf(stableHierarchy);
        this.constructorCount = constructorCount;
        this.hasStaticInitializer = hasStaticInitializer;
        this.normalizedMethodDescriptors = Map.copyOf(normalizedMethodDescriptors);
        this.primitiveOnlyDescriptors = Map.copyOf(primitiveOnlyDescriptors);
        this.staticMethodDescriptors = Set.copyOf(staticMethodDescriptors);
        this.opcodeHistogram = Map.copyOf(opcodeHistogram);
        this.stringConstants = Set.copyOf(stringConstants);
        this.staticFieldTypes = Map.copyOf(staticFieldTypes);
        this.instanceFieldTypes = Map.copyOf(instanceFieldTypes);
        this.externalMethodCalls = Set.copyOf(externalMethodCalls);
        this.caughtExceptions = Set.copyOf(caughtExceptions);
        this.thrownExceptions = Set.copyOf(thrownExceptions);
        this.annotationUsage = Map.copyOf(annotationUsage);
        this.runeLiteEventSubscriptions = Set.copyOf(runeLiteEventSubscriptions);
        this.innerClassCount = innerClassCount;
        this.hasSerialVersionUID = hasSerialVersionUID;
        this.totalMethodCount = normalizedMethodDescriptors.values().stream().mapToInt(i -> i).sum();
        this.totalFieldCount = staticFieldTypes.values().stream().mapToInt(i -> i).sum()
                + instanceFieldTypes.values().stream().mapToInt(i -> i).sum();
        this.fieldTypeReferenceCount = fieldTypeReferenceCount;
        this.methodParameterReferenceCount = methodParameterReferenceCount;
        this.methodReturnReferenceCount = methodReturnReferenceCount;
        this.referencingClasses = Map.copyOf(referencingClasses);
        this.referencedTypes = Set.copyOf(referencedTypes);
    }

    /**
     * Build fingerprints for all classes with cross-reference analysis
     */
    public static Map<String, ClassFingerprint> buildFingerprintsWithReferences(
            Collection<ClassNode> classes) {

        // First pass: build basic fingerprints and collect type references
        Map<String, ClassFingerprint> basicFingerprints = new HashMap<>();
        Map<String, TypeReferenceInfo> typeReferences = new HashMap<>();

        for (ClassNode cn : classes) {
            ClassFingerprint fp = fromClassNode(cn);
            basicFingerprints.put(cn.name, fp);

            // Initialize reference info for this class
            typeReferences.putIfAbsent(cn.name, new TypeReferenceInfo());

            // Count type references in fields
            if (cn.fields != null) {
                for (FieldNode fn : cn.fields) {
                    Type type = Type.getType(fn.desc);
                    if (type.getSort() == Type.OBJECT) {
                        String refClass = type.getInternalName();
                        if (!isStableClass(refClass)) {
                            TypeReferenceInfo info = typeReferences.computeIfAbsent(refClass, k -> new TypeReferenceInfo());
                            info.fieldReferences++;
                            info.referencingClasses.merge(cn.name, 1, Integer::sum);
                        }
                    } else if (type.getSort() == Type.ARRAY) {
                        Type elementType = type.getElementType();
                        if (elementType.getSort() == Type.OBJECT) {
                            String refClass = elementType.getInternalName();
                            if (!isStableClass(refClass)) {
                                TypeReferenceInfo info = typeReferences.computeIfAbsent(refClass, k -> new TypeReferenceInfo());
                                info.fieldReferences++;
                                info.referencingClasses.merge(cn.name, 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            // Count type references in methods
            if (cn.methods != null) {
                for (MethodNode mn : cn.methods) {
                    Type methodType = Type.getMethodType(mn.desc);

                    // Check parameters
                    for (Type argType : methodType.getArgumentTypes()) {
                        if (argType.getSort() == Type.OBJECT) {
                            String refClass = argType.getInternalName();
                            if (!isStableClass(refClass)) {
                                TypeReferenceInfo info = typeReferences.computeIfAbsent(refClass, k -> new TypeReferenceInfo());
                                info.parameterReferences++;
                                info.referencingClasses.merge(cn.name, 1, Integer::sum);
                            }
                        }
                    }

                    // Check return type
                    Type returnType = methodType.getReturnType();
                    if (returnType.getSort() == Type.OBJECT) {
                        String refClass = returnType.getInternalName();
                        if (!isStableClass(refClass)) {
                            TypeReferenceInfo info = typeReferences.computeIfAbsent(refClass, k -> new TypeReferenceInfo());
                            info.returnReferences++;
                            info.referencingClasses.merge(cn.name, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        // Second pass: enhance fingerprints with reference counts
        Map<String, ClassFingerprint> enhanced = new HashMap<>();
        for (Map.Entry<String, ClassFingerprint> entry : basicFingerprints.entrySet()) {
            String className = entry.getKey();
            ClassFingerprint original = entry.getValue();
            TypeReferenceInfo refInfo = typeReferences.getOrDefault(className, new TypeReferenceInfo());

            enhanced.put(className, withReferenceInfo(original,
                    refInfo.fieldReferences,
                    refInfo.parameterReferences,
                    refInfo.returnReferences,
                    refInfo.referencingClasses));
        }

        return enhanced;
    }

    /**
     * Helper class to track type reference information during analysis
     */
    private static class TypeReferenceInfo {
        int fieldReferences = 0;
        int parameterReferences = 0;
        int returnReferences = 0;
        Map<String, Integer> referencingClasses = new HashMap<>();
    }

    /**
     * Create a new fingerprint with reference information added
     */
    private static ClassFingerprint withReferenceInfo(
            ClassFingerprint original,
            int fieldRefs,
            int paramRefs,
            int returnRefs,
            Map<String, Integer> referencingClasses) {

        return new ClassFingerprint(
                original.internalName,
                original.classType,
                original.stableHierarchy,
                original.constructorCount,
                original.hasStaticInitializer,
                original.normalizedMethodDescriptors,
                original.primitiveOnlyDescriptors,
                original.staticMethodDescriptors,
                original.opcodeHistogram,
                original.stringConstants,
                original.staticFieldTypes,
                original.instanceFieldTypes,
                original.externalMethodCalls,
                original.caughtExceptions,
                original.thrownExceptions,
                original.annotationUsage,
                original.runeLiteEventSubscriptions,
                original.innerClassCount,
                original.hasSerialVersionUID,
                fieldRefs,
                paramRefs,
                returnRefs,
                referencingClasses,
                original.referencedTypes
        );
    }

    /**
     * Build fingerprint from an ASM ClassNode (basic version without references)
     */
    public static ClassFingerprint fromClassNode(ClassNode cn) {
        String internalName = cn.name;
        ClassType type = deduceClassType(cn);

        Set<String> stableHierarchy = new HashSet<>();
        Map<String, Integer> normalizedMethodDescriptors = new HashMap<>();
        Map<String, Integer> primitiveOnlyDescriptors = new HashMap<>();
        Set<String> staticMethodDescriptors = new HashSet<>();
        Map<String, Integer> opcodeHist = new HashMap<>();
        Set<String> stringConsts = new HashSet<>();
        Map<String, Integer> staticFieldTypes = new HashMap<>();
        Map<String, Integer> instanceFieldTypes = new HashMap<>();
        Set<String> externalMethodCalls = new HashSet<>();
        Set<String> caughtExceptions = new HashSet<>();
        Set<String> thrownExceptions = new HashSet<>();
        Map<String, Integer> annotationUsage = new HashMap<>();
        Set<String> runeLiteEventSubscriptions = new HashSet<>();
        Set<String> referencedTypes = new HashSet<>();

        // Only add stable (non-obfuscated) hierarchy
        if (cn.superName != null && isStableClass(cn.superName)) {
            stableHierarchy.add(cn.superName);
        }

        // Check ALL interfaces
        if (cn.interfaces != null) {
            for (String iface : cn.interfaces) {
                if (isStableClass(iface)) {
                    stableHierarchy.add(iface);
                }
            }
        }

        // Process annotations
        processAnnotations(cn.visibleAnnotations, annotationUsage);
        processAnnotations(cn.invisibleAnnotations, annotationUsage);

        int constructorCount = 0;
        boolean hasStaticInit = false;

        // Analyze fields
        boolean hasSerialVersionUID = false;
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                String desc = fn.desc;

                if ("serialVersionUID".equals(fn.name) &&
                        "J".equals(desc) &&
                        (fn.access & Opcodes.ACC_STATIC) != 0) {
                    hasSerialVersionUID = true;
                }

                // Track field types
                if ((fn.access & Opcodes.ACC_STATIC) != 0) {
                    staticFieldTypes.merge(desc, 1, Integer::sum);
                } else {
                    instanceFieldTypes.merge(desc, 1, Integer::sum);
                }

                // Track referenced types
                Type fieldType = Type.getType(desc);
                addReferencedType(fieldType, referencedTypes);
            }
        }

        // Analyze methods
        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                String name = mn.name;
                String desc = mn.desc;

                if ("<init>".equals(name)) {
                    constructorCount++;
                } else if ("<clinit>".equals(name)) {
                    hasStaticInit = true;
                }

                // Track referenced types in method descriptor
                Type methodType = Type.getMethodType(desc);
                for (Type argType : methodType.getArgumentTypes()) {
                    addReferencedType(argType, referencedTypes);
                }
                addReferencedType(methodType.getReturnType(), referencedTypes);

                // Always count primitive-only descriptors
                if (isPrimitiveOnlyDescriptor(desc)) {
                    primitiveOnlyDescriptors.merge(desc, 1, Integer::sum);
                }

                // Normalize descriptors by replacing obfuscated object types
                String normalized = normalizeDescriptor(desc);
                normalizedMethodDescriptors.merge(normalized, 1, Integer::sum);

                // For static methods, store normalized descriptor
                if ((mn.access & Opcodes.ACC_STATIC) != 0 && !"<clinit>".equals(name)) {
                    staticMethodDescriptors.add(normalized);
                }

                // Check for RuneLite event subscriptions
                if (mn.visibleAnnotations != null) {
                    for (AnnotationNode an : mn.visibleAnnotations) {
                        if ("Lnet/runelite/api/events/Subscribe;".equals(an.desc)) {
                            Type[] args = Type.getArgumentTypes(mn.desc);
                            if (args.length == 1 && args[0].getSort() == Type.OBJECT) {
                                String eventType = args[0].getInternalName();
                                if (isStableClass(eventType)) {
                                    runeLiteEventSubscriptions.add(eventType);
                                }
                            }
                        }
                    }
                }

                // Collect thrown exceptions
                if (mn.exceptions != null) {
                    for (String ex : mn.exceptions) {
                        if (isStableClass(ex)) {
                            thrownExceptions.add(ex);
                        }
                    }
                }

                // Process method annotations
                processAnnotations(mn.visibleAnnotations, annotationUsage);
                processAnnotations(mn.invisibleAnnotations, annotationUsage);

                // Analyze bytecode
                if ((mn.access & Opcodes.ACC_ABSTRACT) == 0 && mn.instructions != null) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        int opcode = insn.getOpcode();
                        if (opcode >= 0) {
                            String opname = Printer.OPCODES[opcode];
                            if (opname == null) opname = "OP" + opcode;
                            opcodeHist.merge(opname, 1, Integer::sum);
                        }

                        // Track external method calls
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (isStableClass(min.owner)) {
                                externalMethodCalls.add(min.owner + "." + min.name + min.desc);
                            }
                        }

                        // Track string constants
                        if (insn instanceof LdcInsnNode) {
                            Object cst = ((LdcInsnNode) insn).cst;
                            if (cst instanceof String) {
                                stringConsts.add((String) cst);
                            }
                        }
                    }

                    // Track caught exceptions
                    if (mn.tryCatchBlocks != null) {
                        for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
                            if (tcb.type != null && isStableClass(tcb.type)) {
                                caughtExceptions.add(tcb.type);
                            }
                        }
                    }
                }
            }
        }

        // Count inner classes
        int innerClassCount = 0;
        if (cn.innerClasses != null) {
            for (InnerClassNode icn : cn.innerClasses) {
                if (cn.name.equals(icn.outerName) ||
                        (icn.name != null && icn.name.startsWith(cn.name + "$"))) {
                    innerClassCount++;
                }
            }
        }

        return new ClassFingerprint(
                internalName,
                type,
                stableHierarchy,
                constructorCount,
                hasStaticInit,
                normalizedMethodDescriptors,
                primitiveOnlyDescriptors,
                staticMethodDescriptors,
                opcodeHist,
                stringConsts,
                staticFieldTypes,
                instanceFieldTypes,
                externalMethodCalls,
                caughtExceptions,
                thrownExceptions,
                annotationUsage,
                runeLiteEventSubscriptions,
                innerClassCount,
                hasSerialVersionUID,
                0, // fieldTypeReferenceCount - filled in by buildFingerprintsWithReferences
                0, // methodParameterReferenceCount
                0, // methodReturnReferenceCount
                new HashMap<>(), // referencingClasses
                referencedTypes
        );
    }

    /**
     * Add a type to the referenced types set (normalized)
     */
    private static void addReferencedType(Type type, Set<String> referencedTypes) {
        if (type.getSort() == Type.OBJECT) {
            String className = type.getInternalName();
            if (!isStableClass(className)) {
                referencedTypes.add(className);
            }
        } else if (type.getSort() == Type.ARRAY) {
            Type elementType = type.getElementType();
            if (elementType.getSort() == Type.OBJECT) {
                String className = elementType.getInternalName();
                if (!isStableClass(className)) {
                    referencedTypes.add(className);
                }
            }
        }
    }

    /**
     * Validate and potentially correct a class mapping based on type references
     */
    public static Map<String, String> validateClassMapping(
            Map<String, String> initialMapping,
            Map<String, ClassFingerprint> oldFingerprints,
            Map<String, ClassFingerprint> newFingerprints) {

        Map<String, String> validated = new HashMap<>(initialMapping);
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 10) {
            changed = false;
            iterations++;

            for (Map.Entry<String, String> entry : new HashMap<>(validated).entrySet()) {
                String oldClass = entry.getKey();
                String currentNewClass = entry.getValue();

                ClassFingerprint oldFp = oldFingerprints.get(oldClass);
                ClassFingerprint currentNewFp = newFingerprints.get(currentNewClass);

                if (oldFp == null || currentNewFp == null) continue;

                // Check if referenced types map correctly
                int matches = 0;
                int mismatches = 0;

                for (String oldRef : oldFp.referencedTypes) {
                    String mappedRef = validated.get(oldRef);
                    if (mappedRef != null) {
                        if (currentNewFp.referencedTypes.contains(mappedRef)) {
                            matches++;
                        } else {
                            mismatches++;
                        }
                    }
                }

                // Also check who references this class
                int refMatches = 0;
                int refMismatches = 0;
                for (Map.Entry<String, Integer> refEntry : oldFp.referencingClasses.entrySet()) {
                    String oldReferencer = refEntry.getKey();
                    String mappedReferencer = validated.get(oldReferencer);
                    if (mappedReferencer != null) {
                        if (currentNewFp.referencingClasses.containsKey(mappedReferencer)) {
                            refMatches++;
                        } else {
                            refMismatches++;
                        }
                    }
                }

                // If too many mismatches, try to find a better match
                double mismatchRatio = (mismatches + refMismatches) /
                        (double) Math.max(1, matches + mismatches + refMatches + refMismatches);

                if (mismatchRatio > 0.5 && (mismatches + refMismatches) > 2) {
                    // Look for a better match among unmapped new classes
                    String betterMatch = null;
                    double bestScore = oldFp.similarity(currentNewFp);

                    Set<String> usedNewClasses = new HashSet<>(validated.values());

                    for (Map.Entry<String, ClassFingerprint> newEntry : newFingerprints.entrySet()) {
                        String newClass = newEntry.getKey();
                        if (usedNewClasses.contains(newClass)) continue;

                        ClassFingerprint newFp = newEntry.getValue();

                        // Quick check: if reference counts are very different, skip
                        if (Math.abs(oldFp.fieldTypeReferenceCount - newFp.fieldTypeReferenceCount) > 10) {
                            continue;
                        }

                        double score = oldFp.similarity(newFp);
                        if (score > bestScore) {
                            // Additional validation: check type consistency
                            int newMatches = 0;
                            for (String oldRef : oldFp.referencedTypes) {
                                String mappedRef = validated.get(oldRef);
                                if (mappedRef != null && newFp.referencedTypes.contains(mappedRef)) {
                                    newMatches++;
                                }
                            }

                            if (newMatches > matches) {
                                betterMatch = newClass;
                                bestScore = score;
                            }
                        }
                    }

                    if (betterMatch != null) {
                        validated.put(oldClass, betterMatch);
                        changed = true;
                        //System.out.println("Validation: Remapped " + oldClass + " from " + currentNewClass + " to " + betterMatch);
                    }
                }
            }
        }

        return validated;
    }

    static boolean isStableClass(String internalName) {
        return internalName.startsWith("java/") ||
                internalName.startsWith("javax/") ||
                internalName.startsWith("sun/") ||
                internalName.startsWith("net/runelite/") || // RuneLite APIs are stable
                internalName.startsWith("com/sun/") ||
                internalName.startsWith("jdk/") ||
                internalName.startsWith("org/w3c/") ||
                internalName.startsWith("org/xml/") ||
                internalName.startsWith("org/omg/");
    }

    private static void processAnnotations(List<AnnotationNode> annotations,
                                           Map<String, Integer> usage) {
        if (annotations != null) {
            for (AnnotationNode an : annotations) {
                String desc = an.desc;
                String type = desc.substring(1, desc.length() - 1); // Remove L and ;
                if (isStableClass(type)) {
                    usage.merge(type, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Normalizes a method descriptor by replacing obfuscated object types with placeholders
     * while preserving stable types and primitive types.
     */
    private static String normalizeDescriptor(String desc) {
        StringBuilder result = new StringBuilder();
        int i = 0;

        // Parse parameters
        if (desc.charAt(i) != '(') throw new IllegalArgumentException("Invalid descriptor");
        result.append('(');
        i++; // skip '('

        while (i < desc.length() && desc.charAt(i) != ')') {
            i = parseAndNormalizeType(desc, i, result);
        }

        result.append(')');
        i++; // skip ')'

        // Parse return type
        parseAndNormalizeType(desc, i, result);

        return result.toString();
    }

    private static int parseAndNormalizeType(String desc, int start, StringBuilder out) {
        char c = desc.charAt(start);

        switch (c) {
            case 'B': case 'C': case 'D': case 'F': case 'I': case 'J': case 'S': case 'Z': case 'V':
                out.append(c);
                return start + 1;

            case '[':
                out.append('[');
                return parseAndNormalizeType(desc, start + 1, out);

            case 'L':
                int end = desc.indexOf(';', start);
                String className = desc.substring(start + 1, end);

                if (isStableClass(className)) {
                    // Keep stable class names
                    out.append('L').append(className).append(';');
                } else {
                    // Replace obfuscated class with placeholder
                    out.append("#");
                }
                return end + 1;

            default:
                throw new IllegalArgumentException("Invalid type: " + c);
        }
    }

    private static boolean isPrimitiveOnlyDescriptor(String desc) {
        // Check if descriptor contains only primitives and stable classes
        for (int i = 0; i < desc.length(); i++) {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                String className = desc.substring(i + 1, end);
                if (!isStableClass(className)) {
                    return false;
                }
                i = end;
            }
        }
        return true;
    }

    private static ClassType deduceClassType(ClassNode cn) {
        if ((cn.access & Opcodes.ACC_ANNOTATION) != 0) return ClassType.ANNOTATION;
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) return ClassType.INTERFACE;
        if ((cn.access & Opcodes.ACC_ENUM) != 0) return ClassType.ENUM;
        if ((cn.access & Opcodes.ACC_ABSTRACT) != 0) return ClassType.ABSTRACT_CLASS;
        return ClassType.CLASS;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    private static double multisetJaccard(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int minSum = 0;
        int maxSum = 0;
        Set<String> keys = new HashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String k : keys) {
            int va = a.getOrDefault(k, 0);
            int vb = b.getOrDefault(k, 0);
            minSum += Math.min(va, vb);
            maxSum += Math.max(va, vb);
        }
        return maxSum == 0 ? 0.0 : (double) minSum / maxSum;
    }

    private static double cosineHistogram(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        // dot product
        double dot = 0.0;
        for (Map.Entry<String, Integer> e : a.entrySet()) {
            double va = e.getValue();
            double vb = b.getOrDefault(e.getKey(), 0);
            dot += va * vb;
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (normA * normB);
    }

    /**
     * Special comparison for RuneLite patterns
     */
    private static double compareRuneLitePatterns(ClassFingerprint a, ClassFingerprint b) {
        double score = 0.0;
        double weight = 0.0;

        // Compare RuneLite event subscriptions
        if (!a.runeLiteEventSubscriptions.isEmpty() || !b.runeLiteEventSubscriptions.isEmpty()) {
            score += jaccard(a.runeLiteEventSubscriptions, b.runeLiteEventSubscriptions) * 2.0;
            weight += 2.0;
        }

        // Check for RuneLite API usage patterns
        Set<String> aRLCalls = a.externalMethodCalls.stream()
                .filter(call -> call.startsWith("net/runelite/"))
                .collect(Collectors.toSet());
        Set<String> bRLCalls = b.externalMethodCalls.stream()
                .filter(call -> call.startsWith("net/runelite/"))
                .collect(Collectors.toSet());

        if (!aRLCalls.isEmpty() || !bRLCalls.isEmpty()) {
            score += jaccard(aRLCalls, bRLCalls) * 1.5;
            weight += 1.5;
        }

        // Check for RuneLite interfaces
        Set<String> aRLInterfaces = a.stableHierarchy.stream()
                .filter(h -> h.startsWith("net/runelite/"))
                .collect(Collectors.toSet());
        Set<String> bRLInterfaces = b.stableHierarchy.stream()
                .filter(h -> h.startsWith("net/runelite/"))
                .collect(Collectors.toSet());

        if (!aRLInterfaces.isEmpty() && !bRLInterfaces.isEmpty() && aRLInterfaces.equals(bRLInterfaces)) {
            score += 3.0; // Perfect match on RuneLite interfaces is very strong
            weight += 3.0;
        }

        return weight > 0 ? score / weight : 0.0;
    }

    /**
     * Computes a weighted similarity score in [0,1] between two class fingerprints.
     */
    public double similarity(ClassFingerprint other) {
        // Weights adjusted for OSRS/RuneLite context
        double wClassType = 1.0;
        double wStableHierarchy = 4.0;        // Very reliable - includes RuneLite interfaces
        double wPrimitiveDesc = 3.0;          // Primitive-only descriptors are reliable
        double wNormalizedDesc = 1.5;         // Normalized descriptors less reliable
        double wOpcode = 2.0;
        double wExternalCalls = 3.0;          // External API usage patterns
        double wRuneLitePatterns = 3.5;       // RuneLite-specific patterns (very strong signal)
        double wExceptions = 2.0;             // Exception handling patterns
        double wAnnotations = 2.0;            // Annotation usage
        double wStrings = 0.3;                // Often fake in Jagex obfuscation
        double wStaticDesc = 1.5;             // Static method descriptors
        double wFieldTypes = 1.0;
        double wConstructors = 0.5;
        double wStaticInit = 0.2;
        double wInnerClasses = 0.8;
        double wSerialVersionUID = 1.5;
        double wTypeReferences = 3.0;         // Type reference patterns (very important)
        double wReferencedTypes = 2.5;        // Types this class uses

        // Calculate individual scores
        double scoreClassType = this.classType == other.classType ? 1.0 : 0.0;
        double scoreStableHierarchy = jaccard(this.stableHierarchy, other.stableHierarchy);
        double scorePrimitiveDesc = multisetJaccard(this.primitiveOnlyDescriptors, other.primitiveOnlyDescriptors);
        double scoreNormalizedDesc = multisetJaccard(this.normalizedMethodDescriptors, other.normalizedMethodDescriptors);
        double scoreOpcode = cosineHistogram(this.opcodeHistogram, other.opcodeHistogram);
        double scoreExternalCalls = jaccard(this.externalMethodCalls, other.externalMethodCalls);
        double scoreRuneLite = compareRuneLitePatterns(this, other);
        double scoreExceptions = (jaccard(this.caughtExceptions, other.caughtExceptions) +
                jaccard(this.thrownExceptions, other.thrownExceptions)) / 2.0;
        double scoreAnnotations = multisetJaccard(this.annotationUsage, other.annotationUsage);
        double scoreStrings = jaccard(this.stringConstants, other.stringConstants);
        double scoreStaticDesc = jaccard(this.staticMethodDescriptors, other.staticMethodDescriptors);
        double scoreStaticFields = multisetJaccard(this.staticFieldTypes, other.staticFieldTypes);
        double scoreInstanceFields = multisetJaccard(this.instanceFieldTypes, other.instanceFieldTypes);
        double scoreFields = (scoreStaticFields + scoreInstanceFields) / 2.0;
        double scoreConstructors = 1.0 - Math.abs(this.constructorCount - other.constructorCount) /
                (double) Math.max(1, Math.max(this.constructorCount, other.constructorCount));
        double scoreStaticInit = (this.hasStaticInitializer == other.hasStaticInitializer) ? 1.0 : 0.0;
        double scoreInnerClasses = 1.0 - Math.abs(this.innerClassCount - other.innerClassCount) /
                (double) Math.max(1, Math.max(this.innerClassCount, other.innerClassCount));
        double scoreSerialUID = (this.hasSerialVersionUID == other.hasSerialVersionUID) ? 1.0 : 0.0;

        // Calculate type reference similarity
        double fieldRefSim = 1.0 - Math.abs(this.fieldTypeReferenceCount - other.fieldTypeReferenceCount) /
                (double) Math.max(1, Math.max(this.fieldTypeReferenceCount, other.fieldTypeReferenceCount));
        double paramRefSim = 1.0 - Math.abs(this.methodParameterReferenceCount - other.methodParameterReferenceCount) /
                (double) Math.max(1, Math.max(this.methodParameterReferenceCount, other.methodParameterReferenceCount));
        double returnRefSim = 1.0 - Math.abs(this.methodReturnReferenceCount - other.methodReturnReferenceCount) /
                (double) Math.max(1, Math.max(this.methodReturnReferenceCount, other.methodReturnReferenceCount));

        double scoreTypeReferences = (fieldRefSim * 2.0 + paramRefSim + returnRefSim) / 4.0;

        // Give significant bonus if both are highly referenced (likely important classes like ClientPacket)
        if (this.fieldTypeReferenceCount > 10 && other.fieldTypeReferenceCount > 10 &&
                Math.abs(this.fieldTypeReferenceCount - other.fieldTypeReferenceCount) < 5) {
            scoreTypeReferences = Math.min(1.0, scoreTypeReferences * 1.3);
        }

        // Compare which classes reference this one
        double scoreReferencingClasses = jaccard(
                this.referencingClasses.keySet(),
                other.referencingClasses.keySet()
        );

        // Compare types this class references (normalized)
        double scoreReferencedTypes = jaccard(this.referencedTypes, other.referencedTypes);

        // Calculate weighted sum
        double numerator =
                wClassType * scoreClassType +
                        wStableHierarchy * scoreStableHierarchy +
                        wPrimitiveDesc * scorePrimitiveDesc +
                        wNormalizedDesc * scoreNormalizedDesc +
                        wOpcode * scoreOpcode +
                        wExternalCalls * scoreExternalCalls +
                        wRuneLitePatterns * scoreRuneLite +
                        wExceptions * scoreExceptions +
                        wAnnotations * scoreAnnotations +
                        wStrings * scoreStrings +
                        wStaticDesc * scoreStaticDesc +
                        wFieldTypes * scoreFields +
                        wConstructors * scoreConstructors +
                        wStaticInit * scoreStaticInit +
                        wInnerClasses * scoreInnerClasses +
                        wSerialVersionUID * scoreSerialUID +
                        wTypeReferences * scoreTypeReferences +
                        wReferencedTypes * scoreReferencedTypes;

        double denominator = wClassType + wStableHierarchy + wPrimitiveDesc + wNormalizedDesc +
                wOpcode + wExternalCalls + wRuneLitePatterns + wExceptions +
                wAnnotations + wStrings + wStaticDesc + wFieldTypes +
                wConstructors + wStaticInit + wInnerClasses + wSerialVersionUID +
                wTypeReferences + wReferencedTypes;

        // Apply size penalty if classes are very different in size
        double sizePenalty = 1.0;
        double methodRatio = Math.min(this.totalMethodCount, other.totalMethodCount) /
                (double) Math.max(1, Math.max(this.totalMethodCount, other.totalMethodCount));
        double fieldRatio = Math.min(this.totalFieldCount, other.totalFieldCount) /
                (double) Math.max(Math.max(1, this.totalFieldCount), Math.max(1, other.totalFieldCount));

        if (methodRatio < 0.5 || fieldRatio < 0.5) {
            sizePenalty = 0.7; // 30% penalty for very different sizes
        } else if (methodRatio < 0.7 || fieldRatio < 0.7) {
            sizePenalty = 0.85; // 15% penalty for somewhat different sizes
        }

        return (numerator / denominator) * sizePenalty;
    }

    @Override
    public String toString() {
        return "ClassFingerprint{" +
                "name=" + internalName +
                ", type=" + classType +
                ", stableHierarchy=" + stableHierarchy +
                ", constructors=" + constructorCount +
                ", hasClinit=" + hasStaticInitializer +
                ", normalizedMethodDesc=" + normalizedMethodDescriptors.size() +
                ", primitiveOnlyDesc=" + primitiveOnlyDescriptors.size() +
                ", staticMethods=" + staticMethodDescriptors.size() +
                ", opcodeHistogramSize=" + opcodeHistogram.size() +
                ", externalCalls=" + externalMethodCalls.size() +
                ", runeLiteEvents=" + runeLiteEventSubscriptions.size() +
                ", stringConsts=" + stringConstants.size() +
                ", staticFieldTypes=" + staticFieldTypes.keySet().size() +
                ", instanceFieldTypes=" + instanceFieldTypes.keySet().size() +
                ", fieldTypeRefs=" + fieldTypeReferenceCount +
                ", paramRefs=" + methodParameterReferenceCount +
                ", returnRefs=" + methodReturnReferenceCount +
                ", referencingClasses=" + referencingClasses.size() +
                '}';
    }

    public Map<String, Double> componentSimilarityBreakdown(ClassFingerprint other) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("classType", this.classType == other.classType ? 1.0 : 0.0);
        m.put("stableHierarchy", jaccard(this.stableHierarchy, other.stableHierarchy));
        m.put("primitiveDescriptors", multisetJaccard(this.primitiveOnlyDescriptors, other.primitiveOnlyDescriptors));
        m.put("normalizedDescriptors", multisetJaccard(this.normalizedMethodDescriptors, other.normalizedMethodDescriptors));
        m.put("opcodeHistogram", cosineHistogram(this.opcodeHistogram, other.opcodeHistogram));
        m.put("externalMethodCalls", jaccard(this.externalMethodCalls, other.externalMethodCalls));
        m.put("runeLitePatterns", compareRuneLitePatterns(this, other));
        m.put("exceptions", (jaccard(this.caughtExceptions, other.caughtExceptions) +
                jaccard(this.thrownExceptions, other.thrownExceptions)) / 2.0);
        m.put("annotations", multisetJaccard(this.annotationUsage, other.annotationUsage));
        m.put("stringConstants", jaccard(this.stringConstants, other.stringConstants));
        m.put("staticMethodDescs", jaccard(this.staticMethodDescriptors, other.staticMethodDescriptors));
        m.put("staticFields", multisetJaccard(this.staticFieldTypes, other.staticFieldTypes));
        m.put("instanceFields", multisetJaccard(this.instanceFieldTypes, other.instanceFieldTypes));
        m.put("constructorCount", 1.0 - Math.abs(this.constructorCount - other.constructorCount) /
                (double) Math.max(1, Math.max(this.constructorCount, other.constructorCount)));
        m.put("staticInit", (this.hasStaticInitializer == other.hasStaticInitializer) ? 1.0 : 0.0);
        m.put("innerClasses", 1.0 - Math.abs(this.innerClassCount - other.innerClassCount) /
                (double) Math.max(1, Math.max(this.innerClassCount, other.innerClassCount)));
        m.put("serialVersionUID", (this.hasSerialVersionUID == other.hasSerialVersionUID) ? 1.0 : 0.0);

        // Type reference scores
        double fieldRefSim = 1.0 - Math.abs(this.fieldTypeReferenceCount - other.fieldTypeReferenceCount) /
                (double) Math.max(1, Math.max(this.fieldTypeReferenceCount, other.fieldTypeReferenceCount));
        double paramRefSim = 1.0 - Math.abs(this.methodParameterReferenceCount - other.methodParameterReferenceCount) /
                (double) Math.max(1, Math.max(this.methodParameterReferenceCount, other.methodParameterReferenceCount));
        double returnRefSim = 1.0 - Math.abs(this.methodReturnReferenceCount - other.methodReturnReferenceCount) /
                (double) Math.max(1, Math.max(this.methodReturnReferenceCount, other.methodReturnReferenceCount));

        m.put("fieldTypeReferences", fieldRefSim);
        m.put("parameterReferences", paramRefSim);
        m.put("returnReferences", returnRefSim);
        m.put("referencingClasses", jaccard(this.referencingClasses.keySet(), other.referencingClasses.keySet()));
        m.put("referencedTypes", jaccard(this.referencedTypes, other.referencedTypes));

        return m;
    }
}