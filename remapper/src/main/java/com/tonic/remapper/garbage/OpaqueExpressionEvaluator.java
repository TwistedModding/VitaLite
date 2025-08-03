package com.tonic.remapper.garbage;

import lombok.RequiredArgsConstructor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

public class OpaqueExpressionEvaluator
{
    public static Number calculatePassableValue(MethodNode mn)
    {
        Type[] args = Type.getArgumentTypes(mn.desc);

        if (args.length == 0)
            return 0;

        int slot = ((mn.access & Opcodes.ACC_STATIC) == 0) ? 1 : 0;
        for (int i = 0; i < args.length - 1; i++)
            slot += args[i].getSize();
        final int lastParamSlot = slot;

        Set<Condition> conditions = new HashSet<>();
        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            int op = insn.getOpcode();
            if (op < 0) continue;

            if (op != Opcodes.ILOAD)
                continue;

            VarInsnNode varInsn = (VarInsnNode) insn;
            if(varInsn.var != lastParamSlot)
                continue;

            JumpInsnNode  jumpInsn;
            AbstractInsnNode constInsn;

            AbstractInsnNode next = varInsn.getNext();
            AbstractInsnNode prev = varInsn.getPrevious();

            if (next instanceof JumpInsnNode && isConditional((JumpInsnNode) next)) {
                jumpInsn  = (JumpInsnNode) next;
                constInsn = prev;
            }
            else if (next != null && next.getNext() instanceof JumpInsnNode && isConditional((JumpInsnNode) next.getNext())) {
                constInsn = next;
                jumpInsn  = (JumpInsnNode) next.getNext();
            } else {
                continue;
            }

            Object constValue;
            LdcKind  ldcKind;

            if (constInsn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) constInsn;
                constValue = ldc.cst;
                ldcKind = LdcUtil.kindOf(ldc);
            } else if (constInsn instanceof IntInsnNode) {
                IntInsnNode ii = (IntInsnNode) constInsn;
                constValue = ii.operand;
                ldcKind = LdcKind.INT;
            } else if (constInsn instanceof InsnNode) {
                constValue = iconstValue(constInsn.getOpcode());
                ldcKind = LdcKind.INT;
            } else {
                continue;
            }

            CondJump condJump = CondJump.fromOpcode(jumpInsn.getOpcode());
            conditions.add(new Condition(condJump, ldcKind, constValue));
        }

        if (conditions.isEmpty()) return 0;

        long min, max;
        Type lastType = args[args.length - 1];
        switch (lastType.getSort()) {
            case Type.BYTE:
                min = Byte.MIN_VALUE;
                max = Byte.MAX_VALUE;
                break;
            case Type.SHORT:
                min = Short.MIN_VALUE;
                max = Short.MAX_VALUE;
                break;
            // …
            default:
                min = Integer.MIN_VALUE;
                max = Integer.MAX_VALUE;
        }

        Set<Long> neq = new HashSet<>();

        for (Condition c : conditions) {
            long v = ((Number) c.value).longValue();
            switch (c.condJump) {
                case IF_ICMPEQ:
                case IFEQ:
                    min = max = v;
                    break;
                case IF_ICMPNE:
                case IFNE:
                    neq.add(v);
                    break;
                case IF_ICMPLT:
                case IFLT:
                    max = Math.min(max, v - 1);
                    break;
                case IF_ICMPLE:
                case IFLE:
                    max = Math.min(max, v);
                    break;
                case IF_ICMPGT:
                case IFGT:
                    min = Math.max(min, v + 1);
                    break;
                case IF_ICMPGE:
                case IFGE:
                    min = Math.max(min, v);
                    break;
                default:
                    break;
            }
        }
        if (min > max) return 0;

        long cand = min;
        while (cand <= max && neq.contains(cand)) cand++;
        if (cand > max) return 0;

        return (int) cand;
    }

    private static boolean isConditional(JumpInsnNode j) {
        return CondJump.fromOpcode(j.getOpcode()) != null;
    }

    private static Integer iconstValue(int opcode) {
        switch (opcode) {
            case ICONST_M1: return -1;
            case ICONST_0 : return 0;
            case ICONST_1 : return 1;
            case ICONST_2 : return 2;
            case ICONST_3 : return 3;
            case ICONST_4 : return 4;
            case ICONST_5 : return 5;
            default:        return null;
        }
    }

    @RequiredArgsConstructor
    private static class Condition
    {
        private final CondJump condJump;
        private final LdcKind ldcKind;
        private final Object value;

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Condition)) return false;

            Condition condition = (Condition) o;

            if (condJump != condition.condJump) return false;
            if (ldcKind != condition.ldcKind) return false;
            return value.equals(condition.value);
        }

        @Override
        public int hashCode()
        {
            return (ldcKind.name() + condJump.name() + value).hashCode();
        }
    }
}
