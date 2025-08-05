package com.tonic.remapper.editor.analasys;

import com.tonic.remapper.editor.analasys.DeobPipeline;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;

public final class BytecodeTransformers {

    /** Constant / algebraic folding for ADD, SUB, MUL, DIV, NEG, etc. */
    public static DeobPipeline.MethodTransformer constantFolding() {
        return mn -> {
            InsnList insns = mn.instructions;
            ListIterator<AbstractInsnNode> it = insns.iterator();
            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();

                if (!(insn instanceof InsnNode)) continue;
                int op = insn.getOpcode();

                if (op == Opcodes.INEG || op == Opcodes.LNEG ||
                        op == Opcodes.FNEG || op == Opcodes.DNEG) {
                    // pattern: <const> NEG   =>  <folded-const>
                    AbstractInsnNode prev = insn.getPrevious();
                    Integer folded = constInt(prev);
                    if (folded != null) {
                        insns.set(prev, new LdcInsnNode(-folded));
                        it.remove();                       // drop NEG
                    }
                } else if (op == Opcodes.IADD || op == Opcodes.ISUB ||
                        op == Opcodes.IMUL || op == Opcodes.IDIV ||
                        op == Opcodes.IREM) {
                    // pattern: <const1> <const2> IADD => result
                    Integer b = constInt(insn.getPrevious());
                    Integer a = b != null ? constInt(insn.getPrevious().getPrevious()) : null;
                    if (a != null) {
                        int res;
                        switch (op) {
                            case Opcodes.IADD:
                                res = a + b;
                                break;
                            case Opcodes.ISUB:
                                res = a - b;
                                break;
                            case Opcodes.IMUL:
                                res = a * b;
                                break;
                            case Opcodes.IDIV:
                                res = (b == 0 ? a : a / b);
                                break;
                            case Opcodes.IREM:
                                res = (b == 0 ? a : a % b);
                                break;
                            default:
                                res = a;
                                break;
                        }
                        // replace a, b and the op with a single LDC
                        insns.set(insn.getPrevious().getPrevious(), new LdcInsnNode(res));
                        insns.remove(insn.getPrevious());
                        it.remove();
                    }
                }
            }
        };
    }

    /** Remove instructions proven to be unreachable. */
    public static DeobPipeline.MethodTransformer deadCodeElimination() {
        return mn -> {
            Analyzer<BasicValue> analyzer =
                    new Analyzer<>(new BasicInterpreter());
            Frame<BasicValue>[] frames = analyzer.analyze("dummy/Owner", mn);

            for (int i = 0; i < frames.length; i++) {
                if (frames[i] == null) {          // unreachable
                    mn.instructions.remove(mn.instructions.get(i));
                }
            }
        };
    }

    public static DeobPipeline.MethodTransformer stripTryCatch() {
        return mn -> {
            Iterator<TryCatchBlockNode> it = mn.tryCatchBlocks.iterator();
            Set<LabelNode> handlersToRemove = new HashSet<>();

            while (it.hasNext()) {
                TryCatchBlockNode tcb = it.next();

                // Check if this is likely a synchronized block handler
                if (isSynchronizedHandler(tcb)) {
                    continue; // Keep this try-catch
                }

                // Check if this is the obfuscation pattern (catches RuntimeException)
                if ("java/lang/RuntimeException".equals(tcb.type)) {
                    handlersToRemove.add(tcb.handler);
                    it.remove();
                }
            }

            // Remove the handler code for obfuscation handlers only
            List<AbstractInsnNode> copy = copy(mn.instructions);
            for (AbstractInsnNode insn : copy) {
                if (handlersToRemove.contains(insn)) {
                    AbstractInsnNode cur = insn;
                    while (cur != null && !(cur instanceof LabelNode && cur != insn)) {
                        AbstractInsnNode next = cur.getNext();
                        mn.instructions.remove(cur);
                        cur = next;
                    }
                }
            }
        };
    }

    private static boolean isSynchronizedHandler(TryCatchBlockNode tcb) {
        // Check if handler contains monitorexit
        AbstractInsnNode insn = tcb.handler;
        int count = 0;
        while (insn != null && count < 10) { // Check first 10 instructions
            if (insn.getOpcode() == Opcodes.MONITOREXIT) {
                return true;
            }
            insn = insn.getNext();
            count++;
        }

        // Also check if the try block contains monitorenter
        insn = getLabelTarget(tcb.start);
        count = 0;
        while (insn != null && insn != getLabelTarget(tcb.end) && count < 20) {
            if (insn.getOpcode() == Opcodes.MONITORENTER) {
                return true;
            }
            insn = insn.getNext();
            count++;
        }

        // Check if it catches any throwable (type == null), common for synchronized
        return tcb.type == null;
    }

    private static AbstractInsnNode getLabelTarget(LabelNode label) {
        AbstractInsnNode insn = label;
        while (insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode) {
            insn = insn.getNext();
        }
        return insn;
    }

    private static List<AbstractInsnNode> copy(InsnList insnList) {
        List<AbstractInsnNode> out = new ArrayList<>(insnList.size());
        for (AbstractInsnNode n = insnList.getFirst(); n != null; n = n.getNext()) {
            out.add(n);
        }
        return out;
    }
    private static Integer constInt(AbstractInsnNode n) {
        switch (n.getOpcode()) {
            case Opcodes.ICONST_M1: return -1;
            case Opcodes.ICONST_0 : return 0;
            case Opcodes.ICONST_1 : return 1;
            case Opcodes.ICONST_2 : return 2;
            case Opcodes.ICONST_3 : return 3;
            case Opcodes.ICONST_4 : return 4;
            case Opcodes.ICONST_5 : return 5;

            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return ((IntInsnNode) n).operand;

            case Opcodes.LDC:
                Object cst = ((LdcInsnNode) n).cst;
                return (cst instanceof Integer) ? (Integer) cst : null;

            default:
                return null;
        }
    }

    public static DeobPipeline.MethodTransformer fixLabeledBreaks() {
        return mn -> {
            // Very specific pattern matching for the labeled break issue
            // Pattern: Multiple IFs jumping to same label, followed by GOTO that skips code

            Map<LabelNode, List<JumpInsnNode>> forwardJumps = new HashMap<>();
            List<JumpInsnNode> allJumps = new ArrayList<>();

            // First pass: collect all forward jumps
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof JumpInsnNode) {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    allJumps.add(jump);

                    // Check if it's a forward jump
                    if (isForwardJump(mn, jump)) {
                        forwardJumps.computeIfAbsent(jump.label, k -> new ArrayList<>()).add(jump);
                    }
                }
            }

            // Look for the specific problematic pattern
            for (Map.Entry<LabelNode, List<JumpInsnNode>> entry : forwardJumps.entrySet()) {
                LabelNode targetLabel = entry.getKey();
                List<JumpInsnNode> jumpsToLabel = entry.getValue();

                // Need at least 2 conditional jumps to the same label
                if (jumpsToLabel.size() < 2) continue;

                // Check if there's a GOTO between the conditionals and the target
                JumpInsnNode problematicGoto = findProblematicGoto(mn, jumpsToLabel, targetLabel);

                if (problematicGoto != null) {
                    // Found the pattern! But let's be very careful about fixing it
                    safelyFixPattern(mn, jumpsToLabel, problematicGoto, targetLabel);
                }
            }
        };
    }

    private static boolean isForwardJump(MethodNode mn, JumpInsnNode jump) {
        // Simple forward jump check
        boolean foundJump = false;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn == jump) {
                foundJump = true;
            } else if (foundJump && insn == jump.label) {
                return true;
            }
        }
        return false;
    }

    private static JumpInsnNode findProblematicGoto(MethodNode mn, List<JumpInsnNode> jumpsToLabel,
                                                    LabelNode targetLabel) {
        // Find a GOTO that:
        // 1. Comes after the conditional jumps
        // 2. Comes before the target label
        // 3. Skips some code to reach another label

        // Find the last conditional jump
        AbstractInsnNode lastConditional = null;
        for (AbstractInsnNode insn : mn.instructions) {
            if (jumpsToLabel.contains(insn)) {
                lastConditional = insn;
            }
        }

        if (lastConditional == null) return null;

        // Look for GOTO between last conditional and target
        AbstractInsnNode current = lastConditional.getNext();
        while (current != null && current != targetLabel) {
            if (current instanceof JumpInsnNode && current.getOpcode() == Opcodes.GOTO) {
                JumpInsnNode gotoInsn = (JumpInsnNode) current;

                // Check if this GOTO skips some instructions
                if (skipsInstructions(mn, gotoInsn)) {
                    return gotoInsn;
                }
            }
            current = current.getNext();
        }

        return null;
    }

    private static boolean skipsInstructions(MethodNode mn, JumpInsnNode gotoInsn) {
        // Check if GOTO skips at least one real instruction
        AbstractInsnNode current = gotoInsn.getNext();
        boolean foundRealInsn = false;

        while (current != null && current != gotoInsn.label) {
            if (!(current instanceof LabelNode || current instanceof FrameNode ||
                    current instanceof LineNumberNode)) {
                foundRealInsn = true;
            }
            current = current.getNext();
        }

        return foundRealInsn && current == gotoInsn.label;
    }

    private static void safelyFixPattern(MethodNode mn, List<JumpInsnNode> jumpsToLabel,
                                         JumpInsnNode problematicGoto, LabelNode targetLabel) {
        // Instead of modifying existing jumps, let's restructure by:
        // 1. Moving the code that's being skipped
        // 2. Removing the GOTO

        // Collect the instructions being skipped by the GOTO
        List<AbstractInsnNode> skippedInsns = new ArrayList<>();
        AbstractInsnNode current = problematicGoto.getNext();

        while (current != null && current != problematicGoto.label) {
            skippedInsns.add(current);
            current = current.getNext();
        }

        if (skippedInsns.isEmpty()) return;

        // Check if it's safe to move these instructions
        if (!isSafeToMove(skippedInsns)) return;

        // Remove the problematic GOTO
        mn.instructions.remove(problematicGoto);

        // The skipped instructions now execute in the normal flow
        // This should eliminate the labeled break pattern
    }

    private static boolean isSafeToMove(List<AbstractInsnNode> insns) {
        // Check if these instructions have any labels targeted by other jumps
        // If they do, it's not safe to move them

        for (AbstractInsnNode insn : insns) {
            if (insn instanceof LabelNode) {
                // For now, be conservative and don't move if there are any labels
                // You could make this smarter by checking if the label is actually used
                return false;
            }
        }

        return true;
    }

    // Alternative: Minimal intervention approach
    public static DeobPipeline.MethodTransformer minimalLabelFix() {
        return mn -> {
            // Only fix the most obvious cases to avoid breaking things

            List<AbstractInsnNode> toRemove = new ArrayList<>();

            for (AbstractInsnNode insn : mn.instructions) {
                if (insn.getOpcode() == Opcodes.GOTO) {
                    JumpInsnNode gotoInsn = (JumpInsnNode) insn;

                    // Check for very specific pattern:
                    // GOTO that jumps over a single ASTORE (like: ap = kt.ap)
                    AbstractInsnNode next = gotoInsn.getNext();
                    int count = 0;
                    boolean hasAstore = false;

                    while (next != null && next != gotoInsn.label && count < 3) {
                        if (next.getOpcode() == Opcodes.ASTORE) {
                            hasAstore = true;
                        }
                        if (!(next instanceof LabelNode || next instanceof FrameNode)) {
                            count++;
                        }
                        next = next.getNext();
                    }

                    // Very specific: GOTO that skips exactly one ASTORE
                    if (hasAstore && count == 1 && next == gotoInsn.label) {
                        // Check if there's a conditional right before
                        AbstractInsnNode prev = gotoInsn.getPrevious();
                        while (prev != null && (prev instanceof FrameNode ||
                                prev instanceof LabelNode)) {
                            prev = prev.getPrevious();
                        }

                        if (prev instanceof JumpInsnNode && prev.getOpcode() != Opcodes.GOTO) {
                            // This matches our pattern exactly
                            toRemove.add(gotoInsn);
                        }
                    }
                }
            }

            // Remove the problematic GOTOs
            for (AbstractInsnNode insn : toRemove) {
                mn.instructions.remove(insn);
            }
        };
    }

    // Debug version to understand what's happening
    public static DeobPipeline.MethodTransformer debugLabeledBreaks() {
        return mn -> {
            System.out.println("Analyzing method: " + mn.name);

            // Find potential labeled break patterns
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn.getOpcode() == Opcodes.GOTO) {
                    JumpInsnNode gotoInsn = (JumpInsnNode) insn;

                    // What does this GOTO skip?
                    AbstractInsnNode next = gotoInsn.getNext();
                    List<String> skipped = new ArrayList<>();

                    while (next != null && next != gotoInsn.label) {
                        if (!(next instanceof LabelNode || next instanceof FrameNode)) {
                            skipped.add(getInsnString(next));
                        }
                        next = next.getNext();
                    }

                    if (!skipped.isEmpty()) {
                        System.out.println("  GOTO skips: " + skipped);

                        // Check what jumps to the GOTO's target
                        int jumpsToTarget = 0;
                        for (AbstractInsnNode insn2 : mn.instructions) {
                            if (insn2 instanceof JumpInsnNode) {
                                JumpInsnNode jump = (JumpInsnNode) insn2;
                                if (jump.label == gotoInsn.label && jump != gotoInsn) {
                                    jumpsToTarget++;
                                }
                            }
                        }

                        System.out.println("  Jumps to GOTO target: " + jumpsToTarget);
                    }
                }
            }
        };
    }

    private static String getInsnString(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            return insn.getOpcode() + " " + f.owner + "." + f.name;
        } else if (insn instanceof VarInsnNode) {
            VarInsnNode v = (VarInsnNode) insn;
            return insn.getOpcode() + " " + v.var;
        } else {
            return String.valueOf(insn.getOpcode());
        }
    }
}