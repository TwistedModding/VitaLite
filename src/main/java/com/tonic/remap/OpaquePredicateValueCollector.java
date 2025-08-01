package com.tonic.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Collects the single most frequently observed concrete integer value passed as the last numeric
 * parameter to candidate opaque predicate methods (those with a last numeric param and a guard that
 * throws IllegalStateException).
 */
public class OpaquePredicateValueCollector
{
    /**
     * Scans the provided used methods and returns, for each detected predicate method, the most
     * frequently observed last-argument constant (ties broken by smallest value).
     */
    public static Map<MethodKey, Integer> collectMostFrequent(Map<MethodKey, MethodNode> usedMethods)
    {
        Map<MethodKey, Integer> result = new HashMap<>();

        List<Map.Entry<MethodKey, MethodNode>> entries = new ArrayList<>(usedMethods.entrySet());

        // 1. Identify candidate predicate methods
        List<Map.Entry<MethodKey, MethodNode>> predicateMethods = new ArrayList<>();
        for (Map.Entry<MethodKey, MethodNode> e : entries)
        {
            MethodNode mn = e.getValue();
            Type[] args = Type.getArgumentTypes(mn.desc);
            if (args.length == 0) continue;
            Type last = args[args.length - 1];
            if (!isNumericPrimitive(last)) continue;
            int lastParamLocal = computeLastParamLocalIndex(mn, args);
            if (hasThrowGuardingIllegalState(mn, lastParamLocal))
            {
                predicateMethods.add(e);
            }
        }

        // 2. For each predicate method, collect frequencies of last-arg constants seen at call sites
        for (Map.Entry<MethodKey, MethodNode> predicateEntry : predicateMethods)
        {
            MethodKey predicateKey = predicateEntry.getKey();
            MethodNode predicateNode = predicateEntry.getValue();
            Map<Integer, Integer> freq = new HashMap<>();

            for (Map.Entry<MethodKey, MethodNode> callerEntry : entries)
            {
                MethodNode caller = callerEntry.getValue();
                for (AbstractInsnNode insn : caller.instructions.toArray())
                {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if (!invocationMatches(predicateKey, min)) continue;

                    OptionalInt o = extractLastIntArgConstant(caller, min);
                    if (o.isPresent())
                    {
                        int v = o.getAsInt();
                        freq.merge(v, 1, Integer::sum);
                    }
                }
            }

            if (!freq.isEmpty())
            {
                // pick most frequent; tie-breaker: smallest int
                int best = freq.entrySet().stream()
                        .max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                                .thenComparingInt(e -> -e.getKey())) // reverse so smaller wins on tie
                        .map(Map.Entry::getKey)
                        .orElseThrow();
                result.put(predicateKey, best);
            }
        }

        return result;
    }

    private static boolean isNumericPrimitive(Type t)
    {
        switch (t.getSort())
        {
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.CHAR:
            case Type.BOOLEAN:
                return true;
            default:
                return false;
        }
    }

    private static int computeLastParamLocalIndex(MethodNode mn, Type[] args)
    {
        boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
        int base = isStatic ? 0 : 1;
        int idx = 0;
        for (int i = 0; i < args.length - 1; i++)
        {
            idx += args[i].getSize();
        }
        return base + idx;
    }

    private static boolean hasThrowGuardingIllegalState(MethodNode mn, int paramLocalIndex)
    {
        AbstractInsnNode[] insns = mn.instructions.toArray();
        for (int i = 0; i < insns.length; i++)
        {
            AbstractInsnNode insn = insns[i];
            if (!(insn instanceof VarInsnNode)) continue;
            VarInsnNode vin = (VarInsnNode) insn;
            if (vin.getOpcode() != Opcodes.ILOAD || vin.var != paramLocalIndex) continue;

            AbstractInsnNode next = vin.getNext();
            if (next == null) continue;

            Integer constVal = extractIntConstant(next);
            if (constVal != null)
            {
                AbstractInsnNode afterConst = next.getNext();
                if (afterConst instanceof JumpInsnNode)
                {
                    JumpInsnNode jmp = (JumpInsnNode) afterConst;
                    if (containsIllegalStateThrow(jmp.label) || containsIllegalStateThrow(jmp.getNext()))
                    {
                        return true;
                    }
                }
            }

            if (next instanceof JumpInsnNode)
            {
                JumpInsnNode jmp = (JumpInsnNode) next;
                if (containsIllegalStateThrow(jmp.label) || containsIllegalStateThrow(jmp.getNext()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsIllegalStateThrow(AbstractInsnNode start)
    {
        if (start == null) return false;
        AbstractInsnNode cur = start;
        while (cur != null)
        {
            if (cur.getOpcode() == Opcodes.NEW
                    && cur instanceof TypeInsnNode
                    && "java/lang/IllegalStateException".equals(((TypeInsnNode) cur).desc))
            {
                AbstractInsnNode maybeDup = cur.getNext();
                if (maybeDup != null && maybeDup.getOpcode() == Opcodes.DUP)
                {
                    AbstractInsnNode init = maybeDup.getNext();
                    if (init instanceof MethodInsnNode)
                    {
                        MethodInsnNode min = (MethodInsnNode) init;
                        if (min.getOpcode() == Opcodes.INVOKESPECIAL
                                && "java/lang/IllegalStateException".equals(min.owner)
                                && "<init>".equals(min.name))
                        {
                            AbstractInsnNode afterInit = min.getNext();
                            if (afterInit != null && afterInit.getOpcode() == Opcodes.ATHROW)
                            {
                                return true;
                            }
                        }
                    }
                }
            }
            cur = cur.getNext();
        }
        return false;
    }

    private static boolean invocationMatches(MethodKey target, MethodInsnNode call)
    {
        return target.owner.equals(call.owner)
                && target.name.equals(call.name)
                && target.desc.equals(call.desc);
    }

    // helper frame holding operand stack and simple local constant map
    private static class Frame {
        final Deque<StackValue> stack = new ArrayDeque<>();
        final Map<Integer, StackValue> locals = new HashMap<>();
    }

    /**
     * Attempts to recover the last integer argument passed to callInsn by simulating
     * a window of the bytecode before the call, including simple local variable propagation.
     */
    private static OptionalInt extractLastIntArgConstant(MethodNode caller, MethodInsnNode callInsn) {
        List<AbstractInsnNode> insns = Arrays.asList(caller.instructions.toArray());
        int callIndex = insns.indexOf(callInsn);
        if (callIndex == -1) return OptionalInt.empty();

        int windowStart = Math.max(0, callIndex - 300);
        Frame frame = new Frame();

        for (int i = windowStart; i < callIndex; i++) {
            simulateInsn(insns.get(i), frame);
        }

        if (frame.stack.isEmpty()) return OptionalInt.empty();

        // For both static and instance calls, the last method argument is on top of the stack.
        StackValue top = frame.stack.peekLast();
        if (top != null && top.isConstant && top.constant != null) {
            return OptionalInt.of(top.constant);
        }
        return OptionalInt.empty();
    }

    private static void simulateInsn(AbstractInsnNode insn, Frame frame) {
        int op = insn.getOpcode();
        if (op == -1) return; // labels/frames/line numbers

        // Constants
        switch (op) {
            case Opcodes.ICONST_M1: frame.stack.addLast(StackValue.constant(-1)); return;
            case Opcodes.ICONST_0: frame.stack.addLast(StackValue.constant(0)); return;
            case Opcodes.ICONST_1: frame.stack.addLast(StackValue.constant(1)); return;
            case Opcodes.ICONST_2: frame.stack.addLast(StackValue.constant(2)); return;
            case Opcodes.ICONST_3: frame.stack.addLast(StackValue.constant(3)); return;
            case Opcodes.ICONST_4: frame.stack.addLast(StackValue.constant(4)); return;
            case Opcodes.ICONST_5: frame.stack.addLast(StackValue.constant(5)); return;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                if (insn instanceof IntInsnNode) {
                    frame.stack.addLast(StackValue.constant(((IntInsnNode) insn).operand));
                } else {
                    frame.stack.addLast(StackValue.unknown());
                }
                return;
            default:
                break;
        }

        if (insn instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof Integer) {
                frame.stack.addLast(StackValue.constant((Integer) cst));
            } else {
                frame.stack.addLast(StackValue.unknown());
            }
            return;
        }

        if (insn instanceof VarInsnNode) {
            VarInsnNode vin = (VarInsnNode) insn;
            switch (vin.getOpcode()) {
                case Opcodes.ILOAD:
                case Opcodes.ALOAD:
                case Opcodes.LLOAD:
                case Opcodes.DLOAD:
                case Opcodes.FLOAD: {
                    StackValue val = frame.locals.get(vin.var);
                    frame.stack.addLast(val != null ? val : StackValue.unknown());
                    return;
                }
                case Opcodes.ISTORE:
                case Opcodes.ASTORE:
                case Opcodes.LSTORE:
                case Opcodes.DSTORE:
                case Opcodes.FSTORE: {
                    if (!frame.stack.isEmpty()) {
                        StackValue top = frame.stack.removeLast();
                        if (top.isConstant) {
                            frame.locals.put(vin.var, top);
                        } else {
                            frame.locals.remove(vin.var);
                        }
                    }
                    return;
                }
                default:
                    break;
            }
        }

        // Simple arithmetic / stack ops: degrade to unknowns
        switch (op) {
            case Opcodes.IADD:
            case Opcodes.ISUB:
            case Opcodes.IMUL:
            case Opcodes.IDIV:
            case Opcodes.IREM:
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
            case Opcodes.ISHL:
            case Opcodes.ISHR:
            case Opcodes.IUSHR:
            case Opcodes.LADD:
            case Opcodes.LSUB:
            case Opcodes.LMUL:
            case Opcodes.LDIV:
            case Opcodes.LREM:
                // binary: pop two, push unknown
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
                frame.stack.addLast(StackValue.unknown());
                return;
            case Opcodes.INEG:
            case Opcodes.LNEG:
            case Opcodes.FNEG:
            case Opcodes.DNEG:
                if (!frame.stack.isEmpty()) {
                    StackValue v = frame.stack.removeLast();
                    frame.stack.addLast(v.isConstant ? v : StackValue.unknown());
                }
                return;
            case Opcodes.DUP:
                if (!frame.stack.isEmpty()) {
                    frame.stack.addLast(frame.stack.peekLast());
                }
                return;
            case Opcodes.POP:
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
                return;
            case Opcodes.POP2:
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
                return;
            case Opcodes.SWAP:
                if (frame.stack.size() >= 2) {
                    StackValue a = frame.stack.removeLast();
                    StackValue b = frame.stack.removeLast();
                    frame.stack.addLast(a);
                    frame.stack.addLast(b);
                }
                return;
        }

        if (insn instanceof FieldInsnNode) {
            FieldInsnNode fin = (FieldInsnNode) insn;
            switch (fin.getOpcode()) {
                case Opcodes.GETSTATIC:
                case Opcodes.GETFIELD:
                    frame.stack.addLast(StackValue.unknown());
                    return;
                case Opcodes.PUTSTATIC:
                    if (!frame.stack.isEmpty()) frame.stack.removeLast();
                    return;
                case Opcodes.PUTFIELD:
                    if (!frame.stack.isEmpty()) frame.stack.removeLast(); // value
                    if (!frame.stack.isEmpty()) frame.stack.removeLast(); // objectref
                    return;
                default:
                    break;
            }
        }

        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            Type[] args = Type.getArgumentTypes(m.desc);
            // pop arguments
            for (int i = args.length - 1; i >= 0; i--) {
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
            }
            // instance method has objectref
            if (m.getOpcode() != Opcodes.INVOKESTATIC) {
                if (!frame.stack.isEmpty()) frame.stack.removeLast();
            }
            // push return value if any
            Type ret = Type.getReturnType(m.desc);
            if (ret.getSort() != Type.VOID) {
                frame.stack.addLast(StackValue.unknown());
            }
        }
    }

    private static class StackValue
    {
        final boolean isConstant;
        final Integer constant;

        private StackValue(boolean isConstant, Integer constant)
        {
            this.isConstant = isConstant;
            this.constant = constant;
        }

        static StackValue constant(int v)
        {
            return new StackValue(true, v);
        }

        static StackValue unknown()
        {
            return new StackValue(false, null);
        }
    }

    private static Integer extractIntConstant(AbstractInsnNode insn)
    {
        if (insn == null) return null;
        int op = insn.getOpcode();
        switch (op)
        {
            case Opcodes.ICONST_M1: return -1;
            case Opcodes.ICONST_0: return 0;
            case Opcodes.ICONST_1: return 1;
            case Opcodes.ICONST_2: return 2;
            case Opcodes.ICONST_3: return 3;
            case Opcodes.ICONST_4: return 4;
            case Opcodes.ICONST_5: return 5;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                if (insn instanceof IntInsnNode)
                {
                    return ((IntInsnNode) insn).operand;
                }
                break;
            default:
                if (insn instanceof LdcInsnNode)
                {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Integer)
                    {
                        return (Integer) cst;
                    }
                }
        }
        return null;
    }
}
