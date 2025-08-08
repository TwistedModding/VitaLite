package com.tonic.remapper.buffer;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;

public class BufferMethodAnalyzer {

    public static class BufferMethodFingerprint {
        public final String descriptor;
        public final List<Integer> shiftSequence = new ArrayList<>(); // Exact sequence of shifts in order
        public final List<Integer> addConstants = new ArrayList<>(); // Constants added (like 128 for signed)
        public final int bastoreCount;
        public final boolean hasNegation;

        public BufferMethodFingerprint(MethodNode mn) {
            this.descriptor = mn.desc;

            int bastores = 0;
            boolean negation = false;

            // Track the exact sequence of operations leading to each BASTORE
            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);

                if (insn.getOpcode() == Opcodes.BASTORE) {
                    bastores++;

                    // Look back to find what's being stored
                    int shiftAmount = extractShiftBeforeStore(mn.instructions, i);
                    shiftSequence.add(shiftAmount);

                    // Check for add operations (like +128)
                    Integer addConstant = extractAddConstant(mn.instructions, i);
                    if (addConstant != null) {
                        addConstants.add(addConstant);
                    }
                }

                if (insn.getOpcode() == Opcodes.INEG || insn.getOpcode() == Opcodes.LNEG) {
                    negation = true;
                }
            }

            this.bastoreCount = bastores;
            this.hasNegation = negation;
        }

        /**
         * Extract the shift amount before a BASTORE at the given index
         * Returns 0 if no shift (direct value store)
         */
        private int extractShiftBeforeStore(InsnList instructions, int storeIndex) {
            // Look back from BASTORE to find shift operations
            for (int i = storeIndex - 1; i >= 0 && i > storeIndex - 10; i--) {
                AbstractInsnNode insn = instructions.get(i);

                // Found a shift operation
                if (insn.getOpcode() == Opcodes.ISHR || insn.getOpcode() == Opcodes.LSHR) {
                    // Look for the shift amount
                    AbstractInsnNode prev = insn.getPrevious();
                    if (prev instanceof IntInsnNode && prev.getOpcode() == Opcodes.BIPUSH) {
                        return ((IntInsnNode) prev).operand;
                    }
                    // Check for small constant shifts
                    if (prev.getOpcode() >= Opcodes.ICONST_0 && prev.getOpcode() <= Opcodes.ICONST_5) {
                        return prev.getOpcode() - Opcodes.ICONST_0;
                    }
                    return -1; // Unknown shift amount
                }

                // If we hit another BASTORE or method call, stop looking
                if (insn.getOpcode() == Opcodes.BASTORE ||
                        insn instanceof MethodInsnNode) {
                    break;
                }
            }

            return 0; // No shift found, direct value
        }

        /**
         * Extract constant being added before store (e.g., +128 for signed values)
         */
        private Integer extractAddConstant(InsnList instructions, int storeIndex) {
            for (int i = storeIndex - 1; i >= 0 && i > storeIndex - 10; i--) {
                AbstractInsnNode insn = instructions.get(i);

                if (insn.getOpcode() == Opcodes.IADD) {
                    // Look for the constant being added
                    AbstractInsnNode prev = insn.getPrevious();
                    if (prev instanceof IntInsnNode && prev.getOpcode() == Opcodes.BIPUSH) {
                        return ((IntInsnNode) prev).operand;
                    }
                    if (prev instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) prev).cst;
                        if (cst instanceof Integer) {
                            return (Integer) cst;
                        }
                    }
                }

                // Stop at BASTORE or method call
                if (insn.getOpcode() == Opcodes.BASTORE ||
                        insn instanceof MethodInsnNode) {
                    break;
                }
            }

            return null;
        }

        /**
         * Calculate similarity based on exact shift sequence matching
         */
        public double similarity(BufferMethodFingerprint other) {
            // Shift sequence must match exactly for high confidence
            if (shiftSequence.equals(other.shiftSequence)) {
                double score = 1.0;

                // Bonus for matching add constants
                if (addConstants.equals(other.addConstants)) {
                    score *= 1.2;
                }

                // Bonus for same BASTORE count
                if (bastoreCount == other.bastoreCount) {
                    score *= 1.1;
                }

                return Math.min(score, 1.0);
            }

            // Partial credit for similar patterns
            double score = 0.0;

            // Same number of stores?
            if (bastoreCount == other.bastoreCount) {
                score += 0.3;

                // Check how many shifts match in sequence
                int matches = 0;
                int minLen = Math.min(shiftSequence.size(), other.shiftSequence.size());
                for (int i = 0; i < minLen; i++) {
                    if (shiftSequence.get(i).equals(other.shiftSequence.get(i))) {
                        matches++;
                    }
                }

                if (minLen > 0) {
                    score += 0.5 * (matches / (double) minLen);
                }
            }

            return score;
        }

        /**
         * Create a unique pattern string for this method
         */
        public String getPatternString() {
            return "shifts:" + shiftSequence +
                    ",adds:" + addConstants +
                    ",stores:" + bastoreCount;
        }
    }

    /**
     * Check if a method is a buffer write method
     * Only considers void methods with primitive parameters
     */
    public static boolean isBufferWriteMethod(MethodNode mn) {
        Type methodType = Type.getMethodType(mn.desc);
        Type returnType = methodType.getReturnType();
        Type[] args = methodType.getArgumentTypes();

        // Must be void return
        if (returnType.getSort() != Type.VOID) {
            return false;
        }

        // All parameters must be primitives (or strings/byte arrays for special cases)
        for (Type arg : args) {
            if (arg.getSort() == Type.OBJECT) {
                String desc = arg.getDescriptor();
                if (!desc.equals("Ljava/lang/String;") &&
                        !desc.equals("Ljava/lang/CharSequence;") &&
                        !desc.equals("[B")) {
                    return false;
                }
            }
        }

        // Must have BASTORE operations (writing to byte array)
        boolean hasBASTORE = false;
        boolean hasShifts = false;

        for (AbstractInsnNode insn : mn.instructions) {
            if (insn.getOpcode() == Opcodes.BASTORE) {
                hasBASTORE = true;
            }
            if (insn.getOpcode() == Opcodes.ISHR || insn.getOpcode() == Opcodes.LSHR ||
                    insn.getOpcode() == Opcodes.ISHL || insn.getOpcode() == Opcodes.LSHL) {
                hasShifts = true;
            }
        }

        // Method name length check (obfuscated methods have short names)
        boolean shortName = mn.name.length() <= 3;

        // Accept if it has BASTORE and either shifts or a short name
        return hasBASTORE && (hasShifts || shortName);
    }

    /**
     * Match buffer write methods based on shift patterns
     */
    public static Map<MethodKey, MethodKey> matchBufferMethods(
            Map<MethodKey, MethodNode> oldMethods,
            Map<MethodKey, MethodNode> newMethods,
            String oldBufferClass,
            String newBufferClass,
            double minConfidence) {

        Map<MethodKey, BufferMethodFingerprint> oldFingerprints = new HashMap<>();
        Map<MethodKey, BufferMethodFingerprint> newFingerprints = new HashMap<>();

        // Build fingerprints for buffer write methods only
        for (Map.Entry<MethodKey, MethodNode> entry : oldMethods.entrySet()) {
            if (entry.getKey().owner.equals(oldBufferClass)) {
                MethodNode mn = entry.getValue();
                if (isBufferWriteMethod(mn)) {
                    oldFingerprints.put(entry.getKey(), new BufferMethodFingerprint(mn));
                }
            }
        }

        for (Map.Entry<MethodKey, MethodNode> entry : newMethods.entrySet()) {
            if (entry.getKey().owner.equals(newBufferClass)) {
                MethodNode mn = entry.getValue();
                if (isBufferWriteMethod(mn)) {
                    newFingerprints.put(entry.getKey(), new BufferMethodFingerprint(mn));
                }
            }
        }

        System.out.println("Found " + oldFingerprints.size() + " old buffer write methods and " +
                newFingerprints.size() + " new buffer write methods");

        // Debug: Print patterns
        if (oldFingerprints.size() < 20) { // Only for small sets
            System.out.println("Old patterns:");
            for (Map.Entry<MethodKey, BufferMethodFingerprint> e : oldFingerprints.entrySet()) {
                System.out.println("  " + e.getKey().name + e.getKey().desc +
                        " -> " + e.getValue().getPatternString());
            }
        }

        Map<MethodKey, MethodKey> matches = new HashMap<>();
        Set<MethodKey> usedNew = new HashSet<>();

        // Group by descriptor for type safety
        Map<String, List<Map.Entry<MethodKey, BufferMethodFingerprint>>> oldByDesc =
                groupByDescriptor(oldFingerprints);
        Map<String, List<Map.Entry<MethodKey, BufferMethodFingerprint>>> newByDesc =
                groupByDescriptor(newFingerprints);

        // Match within same descriptor groups
        for (String desc : oldByDesc.keySet()) {
            List<Map.Entry<MethodKey, BufferMethodFingerprint>> oldList = oldByDesc.get(desc);
            List<Map.Entry<MethodKey, BufferMethodFingerprint>> newList = newByDesc.get(desc);

            if (newList == null || newList.isEmpty()) {
                System.out.println("No new methods with descriptor: " + desc);
                continue;
            }

            System.out.println("Matching descriptor " + desc + ": " +
                    oldList.size() + " old vs " + newList.size() + " new");

            // Match based on shift pattern similarity
            for (Map.Entry<MethodKey, BufferMethodFingerprint> oldEntry : oldList) {
                MethodKey oldKey = oldEntry.getKey();
                BufferMethodFingerprint oldFp = oldEntry.getValue();

                MethodKey bestMatch = null;
                double bestScore = minConfidence;

                for (Map.Entry<MethodKey, BufferMethodFingerprint> newEntry : newList) {
                    MethodKey newKey = newEntry.getKey();
                    if (usedNew.contains(newKey)) continue;

                    BufferMethodFingerprint newFp = newEntry.getValue();
                    double score = oldFp.similarity(newFp);

                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = newKey;
                    }
                }

                if (bestMatch != null) {
                    matches.put(oldKey, bestMatch);
                    usedNew.add(bestMatch);
                    System.out.println("  Matched: " + oldKey.name + " -> " + bestMatch.name +
                            " (score: " + String.format("%.3f", bestScore) +
                            ", pattern: " + oldFp.shiftSequence + ")");
                }
            }
        }

        return matches;
    }

    private static Map<String, List<Map.Entry<MethodKey, BufferMethodFingerprint>>>
    groupByDescriptor(Map<MethodKey, BufferMethodFingerprint> fingerprints) {
        Map<String, List<Map.Entry<MethodKey, BufferMethodFingerprint>>> grouped = new HashMap<>();
        for (Map.Entry<MethodKey, BufferMethodFingerprint> entry : fingerprints.entrySet()) {
            String desc = entry.getValue().descriptor;
            grouped.computeIfAbsent(desc, k -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }
}