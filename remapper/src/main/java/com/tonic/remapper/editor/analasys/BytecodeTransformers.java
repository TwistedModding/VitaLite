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
            Set<LabelNode> catchStarts = new HashSet<>();
            for (TryCatchBlockNode tcb : mn.tryCatchBlocks)
                catchStarts.add(tcb.handler);

            List<AbstractInsnNode> copy = copy(mn.instructions);

            for (AbstractInsnNode insn : copy) {
                if (catchStarts.contains(insn)) {
                    // remove until next label or end of handler
                    AbstractInsnNode cur = insn;
                    while (cur != null && !(cur instanceof LabelNode && cur != insn)) {
                        AbstractInsnNode next = cur.getNext();
                        mn.instructions.remove(cur);
                        cur = next;
                    }
                }
            }
            mn.tryCatchBlocks.clear();
        };
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
}