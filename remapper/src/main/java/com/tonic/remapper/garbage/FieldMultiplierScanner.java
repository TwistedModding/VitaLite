package com.tonic.remapper.garbage;

import com.tonic.remapper.fields.FieldKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.math.BigInteger;
import java.util.*;

public final class FieldMultiplierScanner {
    public static final class Pair {
        public final Number decode;
        public final Number encode;
        Pair(long decode, long encode){ this.decode = decode; this.encode = encode; }
        @Override public String toString(){ return "decode=" + decode + " encode=" + encode; }
    }

    public static Map<FieldKey, Pair> scan(List<ClassNode> classes,
                                           Map<FieldKey, FieldNode> fields) {

        Map<FieldKey, Long> dec = new HashMap<>();
        Map<FieldKey, Long> enc = new HashMap<>();

        for (ClassNode cn : classes) {
            for (MethodNode mn : cn.methods) {

                InsnList insn = mn.instructions;
                if (insn == null || insn.size() == 0) continue;

                for (AbstractInsnNode n = insn.getFirst(); n != null; n = n.getNext()) {
                    if (isGet(n)) {
                        FieldInsnNode f = (FieldInsnNode) n;
                        FieldKey key = new FieldKey(f.owner, f.name, f.desc);
                        if (!fields.containsKey(key) || !isNumericField(f.desc))
                            continue;

                        AbstractInsnNode c  = skipMeta(n.getNext());
                        AbstractInsnNode op = skipMeta(c == null ? null : c.getNext());
                        Long k = constValue(c);

                        if (k != null && isMul(op, f.desc)) {
                            dec.putIfAbsent(key, k);
                        }
                    }

                    if (isPut(n)) {
                        FieldInsnNode f = (FieldInsnNode) n;
                        FieldKey key = new FieldKey(f.owner, f.name, f.desc);
                        if (!fields.containsKey(key) || !isNumericField(f.desc))
                            continue;

                        AbstractInsnNode mul = skipMeta(n.getPrevious());
                        if (!isMul(mul, f.desc)) continue;

                        AbstractInsnNode c1 = skipMeta(mul.getPrevious());
                        AbstractInsnNode c2 = (c1 == null) ? null : skipMeta(c1.getPrevious());

                        Long k = constValue(c1);
                        if (k == null) k = constValue(c2);
                        if (k != null)
                            enc.putIfAbsent(key, k);
                    }
                }
            }
        }

        Map<FieldKey, Pair> out = new HashMap<>();
        for (FieldKey k : fields.keySet()) {

            Long d = dec.get(k);
            Long e = enc.get(k);

            if (d == null && e == null) continue;

            if (d == null && e != null) d = modInverse(e, bits(k.desc));
            if (e == null && d != null) e = modInverse(d, bits(k.desc));

            if (d != null && e != null)
                out.put(k, new Pair(d, e));
        }
        return out;
    }

    private static AbstractInsnNode skipMeta(AbstractInsnNode n) {
        while (n != null &&
                (n.getType() == AbstractInsnNode.FRAME ||
                        n.getType() == AbstractInsnNode.LINE  ||
                        n instanceof LabelNode)) {
            n = n.getNext();
        }
        return n;
    }

    private static boolean isGet(AbstractInsnNode n) {
        return n != null && (n.getOpcode() == Opcodes.GETSTATIC || n.getOpcode() == Opcodes.GETFIELD);
    }
    private static boolean isPut(AbstractInsnNode n) {
        return n != null && (n.getOpcode() == Opcodes.PUTSTATIC || n.getOpcode() == Opcodes.PUTFIELD);
    }

    private static boolean isMul(AbstractInsnNode n, String desc) {
        if (n == null) return false;
        int op = n.getOpcode();
        return (desc.equals("I") && op == Opcodes.IMUL) ||
                (desc.equals("J") && op == Opcodes.LMUL);
    }

    private static Long constValue(AbstractInsnNode n) {
        if (n == null) return null;
        switch (n.getOpcode()) {
            case Opcodes.ICONST_M1: return -1L;
            case Opcodes.ICONST_0 :
            case Opcodes.LCONST_0 :
                return 0L;
            case Opcodes.ICONST_1 :
            case Opcodes.LCONST_1 :
                return 1L;
            case Opcodes.ICONST_2 : return 2L;
            case Opcodes.ICONST_3 : return 3L;
            case Opcodes.ICONST_4 : return 4L;
            case Opcodes.ICONST_5 : return 5L;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return (long) ((IntInsnNode) n).operand;
            case Opcodes.LDC:
                Object cst = ((LdcInsnNode) n).cst;
                if (cst instanceof Integer) return ((Integer) cst).longValue();
                if (cst instanceof Long)    return (Long) cst;
                return null;
            default:
                return null;
        }
    }

    private static boolean isNumericField(String desc) {
        return desc.equals("I") || desc.equals("J");
    }

    private static Long modInverse(long x, int bits) {
        long mod = (bits == 32) ? (1L << 32) : 0;
        if (bits == 32) {
            if ((x & 1) == 0) return null;
            return BigInteger.valueOf(x & 0xFFFFFFFFL)
                    .modInverse(BigInteger.ONE.shiftLeft(32))
                    .longValue() & 0xFFFFFFFFL;
        } else {
            if ((x & 1) == 0) return null;
            BigInteger M = BigInteger.ONE.shiftLeft(64);
            return BigInteger.valueOf(x)
                    .mod(M)
                    .modInverse(M)
                    .longValue();
        }
    }

    private static int bits(String desc) { return desc.equals("I") ? 32 : 64; }

    private FieldMultiplierScanner(){}
}
