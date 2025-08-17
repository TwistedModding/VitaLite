package com.tonic.injector.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.Objects;

public final class LdcRewriter {
    private static final Map<String, String> PRIM_DESC = Map.of(
            "void","V","boolean","Z","byte","B","char","C",
            "short","S","int","I","float","F","long","J","double","D"
    );

    private LdcRewriter() {}

    public static int rewriteString(MethodNode mn, String from, String to) {
        int hits = 0;

        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof String && Objects.equals(ldc.cst, from)) {
                    ldc.cst = to;
                    hits++;
                }
            }
        }
        return hits;
    }

    public static int rewriteClassRef(MethodNode mn, Type from, Type to) {
        int hits = 0;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof Type && ldc.cst.equals(from)) {
                    ldc.cst = to;
                    hits++;
                }
            }
        }
        return hits;
    }

    /** Rewrite class-literal LDCs from one type to another using FQDN strings. */
    public static int rewriteClassRef(MethodNode mn, String fromFqdn, String toFqdn) {
        Type from = toAsmType(fromFqdn);
        Type to   = toAsmType(toFqdn);

        int hits = 0;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof Type && ldc.cst.equals(from)) {
                    ldc.cst = to;
                    hits++;
                }
            }
        }
        return hits;
    }

    /** Converts inputs like:
     *  "java.lang.String", "java.lang.String[]", "int", "int[]",
     *  "java/lang/String", "java/lang/String[][]",
     *  or raw descriptors "Ljava/lang/String;", "[I"
     *  into an ASM Type.
     */
    private static Type toAsmType(String s) {
        if (s == null || s.isEmpty()) throw new IllegalArgumentException("type string is empty");

        // If it's already a descriptor, just use it.
        if (s.startsWith("[") || (s.startsWith("L") && s.endsWith(";")) || s.length() == 1) {
            return Type.getType(s);
        }

        // Count Java-style array suffixes: "Foo[]", "int[][]", etc.
        int dims = 0;
        while (s.endsWith("[]")) {
            dims++;
            s = s.substring(0, s.length() - 2);
        }

        // Base descriptor (primitive or object)
        String baseDesc = PRIM_DESC.get(s);
        if (baseDesc == null) {
            // Not a primitive: treat as class name.
            // Accept either FQDN ("java.lang.Foo") or internal ("java/lang/Foo").
            String internal = s.contains("/") ? s : s.replace('.', '/');
            baseDesc = 'L' + internal + ';';
        }

        // Prepend array dims if any
        if (dims > 0) {
            StringBuilder sb = new StringBuilder(dims + baseDesc.length());
            for (int i = 0; i < dims; i++) sb.append('[');
            sb.append(baseDesc);
            return Type.getType(sb.toString());
        }
        return Type.getType(baseDesc);
    }
}
