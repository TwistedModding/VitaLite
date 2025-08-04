package com.tonic.remapper.fields;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Analyzes HOW fields are accessed to better distinguish between similar primitive fields.
 * This helps identify patterns like:
 * - Counters (incremented/decremented)
 * - Flags (boolean checks)
 * - Indices (array access)
 * - Constants (read-only)
 * - Multipliers (arithmetic operations)
 */
public class FieldAccessAnalyzer {

    public enum AccessType {
        READ,
        WRITE,
        INCREMENT,
        DECREMENT,
        ARITHMETIC,
        COMPARISON,
        ARRAY_INDEX,
        BOOLEAN_CHECK,
        CONSTANT_LOAD
    }

    public static class FieldAccessProfile {
        public final FieldKey field;
        public final Map<AccessType, Integer> accessCounts = new EnumMap<>(AccessType.class);
        public final Set<Object> constantValues = new HashSet<>(); // for static finals
        public final Set<MethodKey> readMethods = new HashSet<>();
        public final Set<MethodKey> writeMethods = new HashSet<>();
        public boolean isLikelyCounter = false;
        public boolean isLikelyFlag = false;
        public boolean isLikelyIndex = false;
        public boolean isLikelyConstant = false;
        public boolean isLikelyMultiplier = false;

        public FieldAccessProfile(FieldKey field) {
            this.field = field;
        }

        public void analyzePatterns() {
            int reads = accessCounts.getOrDefault(AccessType.READ, 0);
            int writes = accessCounts.getOrDefault(AccessType.WRITE, 0);
            int increments = accessCounts.getOrDefault(AccessType.INCREMENT, 0);
            int decrements = accessCounts.getOrDefault(AccessType.DECREMENT, 0);
            int comparisons = accessCounts.getOrDefault(AccessType.COMPARISON, 0);
            int arrayIndex = accessCounts.getOrDefault(AccessType.ARRAY_INDEX, 0);
            int boolChecks = accessCounts.getOrDefault(AccessType.BOOLEAN_CHECK, 0);
            int arithmetic = accessCounts.getOrDefault(AccessType.ARITHMETIC, 0);

            // Likely a counter if frequently incremented/decremented
            isLikelyCounter = (increments + decrements) > writes / 2 && writes > 0;

            // Likely a flag if used in boolean contexts
            isLikelyFlag = boolChecks > reads / 2 ||
                    (field.desc.equals("Z") && comparisons > 0);

            // Likely an array index if used to access arrays
            isLikelyIndex = arrayIndex > reads / 3;

            // Likely a constant if never written (except initialization)
            isLikelyConstant = writeMethods.size() <= 1 && reads > writes * 3;

            // Likely a multiplier if used in arithmetic
            isLikelyMultiplier = arithmetic > reads / 2 &&
                    (field.desc.equals("I") || field.desc.equals("J"));
        }

        public double similarity(FieldAccessProfile other) {
            if (other == null) return 0.0;

            double score = 0.0;
            double weight = 0.0;

            // Pattern matching
            if (isLikelyCounter && other.isLikelyCounter) { score += 2.0; weight += 2.0; }
            else if (isLikelyCounter || other.isLikelyCounter) { weight += 1.0; }

            if (isLikelyFlag && other.isLikelyFlag) { score += 2.0; weight += 2.0; }
            else if (isLikelyFlag || other.isLikelyFlag) { weight += 1.0; }

            if (isLikelyIndex && other.isLikelyIndex) { score += 2.0; weight += 2.0; }
            else if (isLikelyIndex || other.isLikelyIndex) { weight += 1.0; }

            if (isLikelyConstant && other.isLikelyConstant) { score += 1.5; weight += 1.5; }
            else if (isLikelyConstant || other.isLikelyConstant) { weight += 0.75; }

            if (isLikelyMultiplier && other.isLikelyMultiplier) { score += 1.5; weight += 1.5; }
            else if (isLikelyMultiplier || other.isLikelyMultiplier) { weight += 0.75; }

            // Access pattern similarity
            double accessSim = compareAccessPatterns(this.accessCounts, other.accessCounts);
            score += accessSim * 3.0;
            weight += 3.0;

            // Read/write ratio similarity
            double thisRatio = readMethods.size() / (double) Math.max(1, writeMethods.size());
            double otherRatio = other.readMethods.size() / (double) Math.max(1, other.writeMethods.size());
            double ratioDiff = Math.abs(thisRatio - otherRatio);
            double ratioSim = 1.0 / (1.0 + ratioDiff);
            score += ratioSim;
            weight += 1.0;

            return weight > 0 ? score / weight : 0.0;
        }

        private double compareAccessPatterns(Map<AccessType, Integer> a, Map<AccessType, Integer> b) {
            if (a.isEmpty() && b.isEmpty()) return 1.0;

            Set<AccessType> allTypes = new HashSet<>(a.keySet());
            allTypes.addAll(b.keySet());

            double totalDiff = 0.0;
            double totalCount = 0.0;

            for (AccessType type : allTypes) {
                int countA = a.getOrDefault(type, 0);
                int countB = b.getOrDefault(type, 0);
                totalCount += Math.max(countA, countB);
                totalDiff += Math.abs(countA - countB);
            }

            return totalCount > 0 ? 1.0 - (totalDiff / (2.0 * totalCount)) : 0.0;
        }
    }

    /**
     * Analyze field access patterns across all methods
     */
    public static Map<FieldKey, FieldAccessProfile> analyzeFieldAccess(
            List<ClassNode> classes,
            Map<FieldKey, FieldNode> fields) {

        Map<FieldKey, FieldAccessProfile> profiles = new HashMap<>();

        // Initialize profiles
        for (FieldKey key : fields.keySet()) {
            profiles.put(key, new FieldAccessProfile(key));
        }

        // Analyze each method
        for (ClassNode cn : classes) {
            for (MethodNode mn : cn.methods) {
                analyzeMethod(cn.name, mn, profiles);
            }
        }

        // Compute patterns
        profiles.values().forEach(FieldAccessProfile::analyzePatterns);

        return profiles;
    }

    private static void analyzeMethod(String className, MethodNode method,
                                      Map<FieldKey, FieldAccessProfile> profiles) {
        MethodKey methodKey = new MethodKey(className, method.name, method.desc);
        AbstractInsnNode[] insns = method.instructions.toArray();

        for (int i = 0; i < insns.length; i++) {
            AbstractInsnNode insn = insns[i];

            if (insn instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                FieldKey fieldKey = new FieldKey(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
                FieldAccessProfile profile = profiles.get(fieldKey);

                if (profile == null) continue;

                // Analyze the context of field access
                analyzeFieldContext(insns, i, fieldInsn, profile, methodKey);
            }
        }
    }

    private static void analyzeFieldContext(AbstractInsnNode[] insns, int index,
                                            FieldInsnNode fieldInsn,
                                            FieldAccessProfile profile,
                                            MethodKey method) {
        int opcode = fieldInsn.getOpcode();

        // Basic read/write
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            profile.accessCounts.merge(AccessType.READ, 1, Integer::sum);
            profile.readMethods.add(method);

            // Check what happens after the read
            if (index < insns.length - 1) {
                AbstractInsnNode next = insns[index + 1];

                // Check for array access
                if (next.getOpcode() >= Opcodes.IALOAD && next.getOpcode() <= Opcodes.SALOAD) {
                    profile.accessCounts.merge(AccessType.ARRAY_INDEX, 1, Integer::sum);
                }

                // Check for comparison
                if (isComparisonInsn(next)) {
                    profile.accessCounts.merge(AccessType.COMPARISON, 1, Integer::sum);
                }

                // Check for boolean usage
                if (next.getOpcode() == Opcodes.IFEQ || next.getOpcode() == Opcodes.IFNE) {
                    profile.accessCounts.merge(AccessType.BOOLEAN_CHECK, 1, Integer::sum);
                }

                // Check for arithmetic
                if (isArithmeticInsn(next)) {
                    profile.accessCounts.merge(AccessType.ARITHMETIC, 1, Integer::sum);
                }
            }

        } else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            profile.accessCounts.merge(AccessType.WRITE, 1, Integer::sum);
            profile.writeMethods.add(method);

            // Check what value is being written
            if (index > 0) {
                AbstractInsnNode prev = insns[index - 1];

                // Check for increment pattern (field = field + 1)
                if (isIncrementPattern(insns, index, fieldInsn)) {
                    profile.accessCounts.merge(AccessType.INCREMENT, 1, Integer::sum);
                } else if (isDecrementPattern(insns, index, fieldInsn)) {
                    profile.accessCounts.merge(AccessType.DECREMENT, 1, Integer::sum);
                }

                // Check for constant assignment
                if (prev instanceof LdcInsnNode) {
                    profile.constantValues.add(((LdcInsnNode) prev).cst);
                    profile.accessCounts.merge(AccessType.CONSTANT_LOAD, 1, Integer::sum);
                } else if (prev.getOpcode() >= Opcodes.ICONST_M1 && prev.getOpcode() <= Opcodes.ICONST_5) {
                    int value = prev.getOpcode() - Opcodes.ICONST_0;
                    profile.constantValues.add(value);
                    profile.accessCounts.merge(AccessType.CONSTANT_LOAD, 1, Integer::sum);
                }
            }
        }
    }

    private static boolean isIncrementPattern(AbstractInsnNode[] insns, int putIndex,
                                              FieldInsnNode putField) {
        // Look for: GETFIELD, ICONST_1, IADD, PUTFIELD pattern
        if (putIndex >= 3) {
            AbstractInsnNode prev1 = insns[putIndex - 1];
            AbstractInsnNode prev2 = insns[putIndex - 2];
            AbstractInsnNode prev3 = insns[putIndex - 3];

            if (prev1.getOpcode() == Opcodes.IADD || prev1.getOpcode() == Opcodes.LADD) {
                if (prev2.getOpcode() == Opcodes.ICONST_1 || prev2.getOpcode() == Opcodes.LCONST_1) {
                    if (prev3 instanceof FieldInsnNode) {
                        FieldInsnNode getField = (FieldInsnNode) prev3;
                        return getField.name.equals(putField.name) &&
                                getField.owner.equals(putField.owner);
                    }
                }
            }
        }
        return false;
    }

    private static boolean isDecrementPattern(AbstractInsnNode[] insns, int putIndex,
                                              FieldInsnNode putField) {
        // Similar to increment but with ISUB/LSUB
        if (putIndex >= 3) {
            AbstractInsnNode prev1 = insns[putIndex - 1];
            AbstractInsnNode prev2 = insns[putIndex - 2];
            AbstractInsnNode prev3 = insns[putIndex - 3];

            if (prev1.getOpcode() == Opcodes.ISUB || prev1.getOpcode() == Opcodes.LSUB) {
                if (prev2.getOpcode() == Opcodes.ICONST_1 || prev2.getOpcode() == Opcodes.LCONST_1) {
                    if (prev3 instanceof FieldInsnNode) {
                        FieldInsnNode getField = (FieldInsnNode) prev3;
                        return getField.name.equals(putField.name) &&
                                getField.owner.equals(putField.owner);
                    }
                }
            }
        }
        return false;
    }

    private static boolean isComparisonInsn(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return (op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ICMPLE) ||
                (op >= Opcodes.IFEQ && op <= Opcodes.IFLE) ||
                op == Opcodes.LCMP || op == Opcodes.FCMPL ||
                op == Opcodes.FCMPG || op == Opcodes.DCMPL || op == Opcodes.DCMPG;
    }

    private static boolean isArithmeticInsn(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return op >= Opcodes.IADD && op <= Opcodes.LXOR;
    }
}