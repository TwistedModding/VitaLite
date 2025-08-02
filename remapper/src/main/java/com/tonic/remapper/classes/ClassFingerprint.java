package com.tonic.remapper.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import java.util.*;

/**
 * Fingerprint for a class, capturing structural & implementation signals.
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
    public final String superName;
    public final Set<String> interfaces;
    public final int constructorCount;
    public final boolean hasStaticInitializer;
    public final Map<String, Integer> methodDescriptorMultiset;
    public final Set<String> staticMethodSignatures;
    public final Map<String, Integer> opcodeHistogram;
    public final Set<String> stringConstants;
    public final Map<String, Integer> staticFieldTypes;
    public final Map<String, Integer> instanceFieldTypes;
    public final int totalMethodCount;
    public final int totalFieldCount;

    private ClassFingerprint(
            String internalName,
            ClassType classType,
            String superName,
            Set<String> interfaces,
            int constructorCount,
            boolean hasStaticInitializer,
            Map<String, Integer> methodDescriptorMultiset,
            Set<String> staticMethodSignatures,
            Map<String, Integer> opcodeHistogram,
            Set<String> stringConstants,
            Map<String, Integer> staticFieldTypes,
            Map<String, Integer> instanceFieldTypes
    ) {
        this.internalName = internalName;
        this.classType = classType;
        this.superName = superName;
        this.interfaces = Set.copyOf(interfaces);
        this.constructorCount = constructorCount;
        this.hasStaticInitializer = hasStaticInitializer;
        this.methodDescriptorMultiset = Map.copyOf(methodDescriptorMultiset);
        this.staticMethodSignatures = Set.copyOf(staticMethodSignatures);
        this.opcodeHistogram = Map.copyOf(opcodeHistogram);
        this.stringConstants = Set.copyOf(stringConstants);
        this.staticFieldTypes = Map.copyOf(staticFieldTypes);
        this.instanceFieldTypes = Map.copyOf(instanceFieldTypes);
        this.totalMethodCount = methodDescriptorMultiset.values().stream().mapToInt(i -> i).sum();
        this.totalFieldCount = staticFieldTypes.values().stream().mapToInt(i -> i).sum()
                + instanceFieldTypes.values().stream().mapToInt(i -> i).sum();
    }

    /**
     * Build fingerprint from an ASM ClassNode.
     */
    public static ClassFingerprint fromClassNode(ClassNode cn) {
        String internalName = cn.name;
        ClassType type = deduceClassType(cn);
        String superName = cn.superName;
        Set<String> interfaces = cn.interfaces != null ? new HashSet<>(cn.interfaces) : Set.of();

        int constructorCount = 0;
        boolean hasStaticInit = false;

        Map<String, Integer> methodDescriptorMultiset = new HashMap<>();
        Set<String> staticMethodSigs = new HashSet<>();
        Map<String, Integer> opcodeHist = new HashMap<>();
        Set<String> stringConsts = new HashSet<>();

        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                String name = mn.name;
                String desc = mn.desc;

                if ("<init>".equals(name)) {
                    constructorCount++;
                } else if ("<clinit>".equals(name)) {
                    hasStaticInit = true;
                }

                // Count descriptor in multiset (include all methods)
                methodDescriptorMultiset.merge(desc, 1, Integer::sum);

                // static method signatures
                if ((mn.access & Opcodes.ACC_STATIC) != 0) {
                    staticMethodSigs.add(name + desc);
                }

                // opcode histogram (skip abstract/empty)
                if ((mn.access & Opcodes.ACC_ABSTRACT) != 0 || mn.instructions == null) {
                    continue;
                }
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    int opcode = insn.getOpcode();
                    if (opcode < 0) continue;
                    String opname = Printer.OPCODES[opcode];
                    if (opname == null) opname = "OP" + opcode;
                    opcodeHist.merge(opname, 1, Integer::sum);

                    if (insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (cst instanceof String) {
                            stringConsts.add((String) cst);
                        }
                    }
                }
            }
        }

        Map<String, Integer> staticFieldTypes = new HashMap<>();
        Map<String, Integer> instanceFieldTypes = new HashMap<>();
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                String desc = fn.desc;
                if ((fn.access & Opcodes.ACC_STATIC) != 0) {
                    staticFieldTypes.merge(desc, 1, Integer::sum);
                } else {
                    instanceFieldTypes.merge(desc, 1, Integer::sum);
                }
            }
        }

        return new ClassFingerprint(
                internalName,
                type,
                superName,
                interfaces,
                constructorCount,
                hasStaticInit,
                methodDescriptorMultiset,
                staticMethodSigs,
                opcodeHist,
                stringConsts,
                staticFieldTypes,
                instanceFieldTypes
        );
    }

    private static ClassType deduceClassType(ClassNode cn) {
        if ((cn.access & Opcodes.ACC_ANNOTATION) != 0) return ClassType.ANNOTATION;
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) return ClassType.INTERFACE;
        if ((cn.access & Opcodes.ACC_ENUM) != 0) return ClassType.ENUM;
        if ((cn.access & Opcodes.ACC_ABSTRACT) != 0) return ClassType.ABSTRACT_CLASS;  //TODO: Maybe?
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
     * Computes a weighted similarity score in [0,1] between two class fingerprints.
     */
    public double similarity(ClassFingerprint other) {
        // weights
        double wClassType = 1.0;
        double wHierarchy = 1.0;
        double wMethodDesc = 2.0;
        double wOpcode = 2.0;
        double wStrings = 1.5;
        double wStaticMethod = 1.0;
        double wFieldTypes = 1.0;
        double wConstructorVariants = 0.5;
        double wStaticInit = 0.2;

        double scoreClassType = this.classType == other.classType ? 1.0 : 0.0;

        // Hierarchy: super + interfaces
        Set<String> thisHierarchy = new HashSet<>();
        if (this.superName != null) thisHierarchy.add(this.superName);
        thisHierarchy.addAll(this.interfaces);
        Set<String> otherHierarchy = new HashSet<>();
        if (other.superName != null) otherHierarchy.add(other.superName);
        otherHierarchy.addAll(other.interfaces);
        double scoreHierarchy = jaccard(thisHierarchy, otherHierarchy);

        double scoreMethodDesc = multisetJaccard(this.methodDescriptorMultiset, other.methodDescriptorMultiset);
        double scoreOpcode = cosineHistogram(this.opcodeHistogram, other.opcodeHistogram);
        double scoreStrings = jaccard(this.stringConstants, other.stringConstants);

        // Static methods signature overlap
        double scoreStaticMethods = jaccard(this.staticMethodSignatures, other.staticMethodSignatures);

        // Fields: combine static + instance via multiset Jaccard and average
        double scoreStaticFields = multisetJaccard(this.staticFieldTypes, other.staticFieldTypes);
        double scoreInstanceFields = multisetJaccard(this.instanceFieldTypes, other.instanceFieldTypes);
        double scoreFields = (scoreStaticFields + scoreInstanceFields) / 2.0;

        // Constructor variant similarity: penalize if counts differ drastically
        double scoreConstructors = 1.0 - Math.abs(this.constructorCount - other.constructorCount) / (double) Math.max(1, Math.max(this.constructorCount, other.constructorCount));

        double scoreStaticInit = (this.hasStaticInitializer == other.hasStaticInitializer) ? 1.0 : 0.0;

        double numerator =
                wClassType * scoreClassType +
                        wHierarchy * scoreHierarchy +
                        wMethodDesc * scoreMethodDesc +
                        wOpcode * scoreOpcode +
                        wStrings * scoreStrings +
                        wStaticMethod * scoreStaticMethods +
                        wFieldTypes * scoreFields +
                        wConstructorVariants * scoreConstructors +
                        wStaticInit * scoreStaticInit;

        double denominator = wClassType + wHierarchy + wMethodDesc + wOpcode + wStrings +
                wStaticMethod + wFieldTypes + wConstructorVariants + wStaticInit;

        return numerator / denominator;
    }

    @Override
    public String toString() {
        return "ClassFingerprint{" +
                "name=" + internalName +
                ", type=" + classType +
                ", super=" + superName +
                ", interfaces=" + interfaces +
                ", constructors=" + constructorCount +
                ", hasClinit=" + hasStaticInitializer +
                ", methodDescKeys=" + methodDescriptorMultiset.keySet().size() +
                ", staticMethods=" + staticMethodSignatures.size() +
                ", opcodeHistogramSize=" + opcodeHistogram.size() +
                ", stringConsts=" + stringConstants.size() +
                ", staticFieldTypes=" + staticFieldTypes.keySet().size() +
                ", instanceFieldTypes=" + instanceFieldTypes.keySet().size() +
                '}';
    }

    public Map<String, Double> componentSimilarityBreakdown(ClassFingerprint other) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("classType", this.classType == other.classType ? 1.0 : 0.0);
        Set<String> thisHierarchy = new HashSet<>();
        if (this.superName != null) thisHierarchy.add(this.superName);
        thisHierarchy.addAll(this.interfaces);
        Set<String> otherHierarchy = new HashSet<>();
        if (other.superName != null) otherHierarchy.add(other.superName);
        otherHierarchy.addAll(other.interfaces);
        m.put("hierarchy", jaccard(thisHierarchy, otherHierarchy));
        m.put("methodDescriptor", multisetJaccard(this.methodDescriptorMultiset, other.methodDescriptorMultiset));
        m.put("opcodeHistogram", cosineHistogram(this.opcodeHistogram, other.opcodeHistogram));
        m.put("stringConstants", jaccard(this.stringConstants, other.stringConstants));
        m.put("staticMethodSigs", jaccard(this.staticMethodSignatures, other.staticMethodSignatures));
        m.put("staticFields", multisetJaccard(this.staticFieldTypes, other.staticFieldTypes));
        m.put("instanceFields", multisetJaccard(this.instanceFieldTypes, other.instanceFieldTypes));
        m.put("constructorCount", 1.0 - Math.abs(this.constructorCount - other.constructorCount) / (double) Math.max(1, Math.max(this.constructorCount, other.constructorCount)));
        m.put("staticInit", (this.hasStaticInitializer == other.hasStaticInitializer) ? 1.0 : 0.0);
        return m;
    }
}
