package com.tonic.remapper.garbage;

import org.objectweb.asm.Opcodes;

/**
 * All conditional-branch opcodes that appear in JumpInsnNode.
 */
public enum CondJump {

    // zero-/null-tests
    IFEQ (Opcodes.IFEQ),        // ==
    IFNE (Opcodes.IFNE),        // !=
    IFLT (Opcodes.IFLT),        //  <
    IFGE (Opcodes.IFGE),        // >=
    IFGT (Opcodes.IFGT),        //  >
    IFLE (Opcodes.IFLE),        // <=
    IFNULL    (Opcodes.IFNULL),     // ref == null
    IFNONNULL (Opcodes.IFNONNULL),  // ref != null

    // int comparisons
    IF_ICMPEQ (Opcodes.IF_ICMPEQ),  // ==
    IF_ICMPNE (Opcodes.IF_ICMPNE),  // !=
    IF_ICMPLT (Opcodes.IF_ICMPLT),  //  <
    IF_ICMPGE (Opcodes.IF_ICMPGE),  // >=
    IF_ICMPGT (Opcodes.IF_ICMPGT),  //  >
    IF_ICMPLE (Opcodes.IF_ICMPLE),  // <=

    // reference comparisons
    IF_ACMPEQ (Opcodes.IF_ACMPEQ),  // ref ==
    IF_ACMPNE (Opcodes.IF_ACMPNE);  // ref !=

    /** The raw opcode value (org.objectweb.asm.Opcodes.*). */
    public final int opcode;

    CondJump(int opcode) {
        this.opcode = opcode;
    }

    /** Quick reverse lookup: opcode → enum (or null if not conditional). */
    private static final java.util.Map<Integer, CondJump> LOOKUP = new java.util.HashMap<>();
    static {
        for (CondJump cj : values()) LOOKUP.put(cj.opcode, cj);
    }
    public static CondJump fromOpcode(int opcode) { return LOOKUP.get(opcode); }
}
