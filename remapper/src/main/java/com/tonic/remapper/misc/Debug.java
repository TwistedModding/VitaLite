package com.tonic.remapper.misc;

import com.tonic.remapper.methods.MethodKey;
import com.tonic.remapper.methods.NormalizedMethod;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.Collectors;
public class Debug {
    /**
     * Compare two methods and print why their score is what it is.
     */
    public static void compare(MethodKey oldKey, MethodNode oldMn,
                               MethodKey newKey, MethodNode newMn) {
        NormalizedMethod oldNorm = new NormalizedMethod(oldKey.owner, oldMn);
        NormalizedMethod newNorm = new NormalizedMethod(newKey.owner, newMn);

        System.out.println("=== Comparison ===");
        System.out.println("Old: " + oldKey);
        System.out.println("New: " + newKey);
        System.out.println();

        System.out.println("Normalized descriptor:");
        System.out.println("  old: " + oldNorm.normalizedDescriptor);
        System.out.println("  new: " + newNorm.normalizedDescriptor);
        System.out.println();

        // Score components
        double descriptorScore = oldNorm.normalizedDescriptor.equals(newNorm.normalizedDescriptor) ? 1.0 : 0.0;
        double invokedJaccard = jaccard(oldNorm.invokedSignatures, newNorm.invokedSignatures);
        double stringJaccard = jaccard(oldNorm.stringConstants, newNorm.stringConstants);
        double opcodeOverlap = overlapCoefficient(oldNorm.opcodeHistogram.keySet(), newNorm.opcodeHistogram.keySet());

        double combined = descriptorScore * 1.0 +
                invokedJaccard * 0.5 +
                stringJaccard * 0.3 +
                opcodeOverlap * 0.2;

        System.out.printf("Component scores:\n" +
                        "  descriptor match: %.3f\n" +
                        "  invoked signatures Jaccard: %.3f\n" +
                        "  string constants Jaccard: %.3f\n" +
                        "  opcode overlap coefficient: %.3f\n",
                descriptorScore, invokedJaccard, stringJaccard, opcodeOverlap);
        System.out.printf("Combined raw score (same formula as matcher): %.3f\n", combined);
        System.out.println();

        System.out.println("Old invoked signatures: " + sortedSample(oldNorm.invokedSignatures));
        System.out.println("New invoked signatures: " + sortedSample(newNorm.invokedSignatures));
        System.out.println();

        System.out.println("Old string constants: " + sortedSample(oldNorm.stringConstants));
        System.out.println("New string constants: " + sortedSample(newNorm.stringConstants));
        System.out.println();

        System.out.println("Old opcode histogram keys: " + sortedSample(oldNorm.opcodeHistogram.keySet()));
        System.out.println("New opcode histogram keys: " + sortedSample(newNorm.opcodeHistogram.keySet()));
        System.out.println();

        System.out.println("Simplified instruction sequence (old):");
        List<String> oldSeq = simplifyInstructions(oldMn);
        printSequenceWithDiffHints(oldSeq, "old");

        System.out.println("Simplified instruction sequence (new):");
        List<String> newSeq = simplifyInstructions(newMn);
        printSequenceWithDiffHints(newSeq, "new");

        System.out.println("=== End Comparison ===");
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 0.0;
        return (double) inter.size() / union.size();
    }

    private static double overlapCoefficient(Set<?> a, Set<?> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<Object> small = a.size() <= b.size() ? new HashSet<>(a) : new HashSet<>(b);
        Set<Object> large = a.size() > b.size() ? new HashSet<>(a) : new HashSet<>(b);
        small.retainAll(large);
        return (double) small.size() / Math.min(a.size(), b.size());
    }

    private static String sortedSample(Collection<?> c) {
        List<String> list = c.stream().map(Object::toString).sorted().collect(Collectors.toList());
        if (list.size() > 20) {
            return list.subList(0, 20) + " ... (+" + (list.size() - 20) + " more)";
        }
        return list.toString();
    }

    private static List<String> simplifyInstructions(MethodNode mn) {
        List<String> out = new ArrayList<>();
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (insn instanceof LabelNode || insn instanceof FrameNode || insn instanceof LineNumberNode) {
                continue; // skip bookkeeping
            }
            String repr = InstructionSimplifier.simplify(insn);
            if (repr != null) {
                out.add(repr);
            }
        }
        return out;
    }

    private static void printSequenceWithDiffHints(List<String> seq, String label) {
        for (int i = 0; i < seq.size(); i++) {
            System.out.printf("  [%3d] %s%n", i, seq.get(i));
        }
        System.out.println();
    }

    /** Basic simplifier to turn ASM insns into comparable strings. */
    static class InstructionSimplifier {
        static String simplify(AbstractInsnNode insn) {
            int op = insn.getOpcode();
            if (op < 0) {
                return null;
            }
            String opname = org.objectweb.asm.util.Printer.OPCODES[op];
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode) insn;
                return String.format("INVOKE %s.%s%s", m.owner, m.name, m.desc);
            }
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode) insn;
                return String.format("FIELD %s.%s %s", f.owner, f.name, f.desc);
            }
            if (insn instanceof VarInsnNode) {
                VarInsnNode v = (VarInsnNode) insn;
                return String.format("%s v%d", opname, v.var);
            }
            if (insn instanceof InsnNode) {
                return opname;
            }
            if (insn instanceof IntInsnNode) {
                return String.format("%s %d", opname, ((IntInsnNode) insn).operand);
            }
            if (insn instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) insn).cst;
                return String.format("LDC(%s)", String.valueOf(cst));
            }
            if (insn instanceof TypeInsnNode) {
                TypeInsnNode t = (TypeInsnNode) insn;
                return String.format("%s %s", opname, t.desc);
            }
            if (insn instanceof JumpInsnNode) {
                return opname;
            }
            if (insn instanceof InvokeDynamicInsnNode) {
                return "INVOKEDYN";
            }
            return opname + "?";
        }
    }
}
