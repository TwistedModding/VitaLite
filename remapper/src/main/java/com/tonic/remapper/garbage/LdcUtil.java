package com.tonic.remapper.garbage;

import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.Type;

public final class LdcUtil {

    /** Returns the constant’s kind; never null. */
    public static LdcKind kindOf(LdcInsnNode ldc) {
        Object c = ldc.cst;
        if (c instanceof Integer)             return LdcKind.INT;
        if (c instanceof Float)               return LdcKind.FLOAT;
        if (c instanceof Long)                return LdcKind.LONG;
        if (c instanceof Double)              return LdcKind.DOUBLE;
        if (c instanceof String)              return LdcKind.STRING;
        if (c instanceof Type)                return LdcKind.CLASS;
        if (c instanceof org.objectweb.asm.Handle)
            return LdcKind.HANDLE;
        return LdcKind.DYNAMIC;   // ConstantDynamic or anything unknown
    }

    /** Convenience: returns the int / long / … value already casted. */
    @SuppressWarnings("unchecked")
    public static <T> T value(LdcInsnNode ldc) {
        return (T) ldc.cst;   // caller casts appropriately
    }

    private LdcUtil() {}
}
