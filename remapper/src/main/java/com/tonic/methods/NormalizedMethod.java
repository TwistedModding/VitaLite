package com.tonic.methods;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Extracts simplified features from a MethodNode for matching, with:
 *  - trailing unused param stripping,
 *  - opaque predicate guard removal,
 *  - full-method obfuscation wrapper unwrapping,
 *  - and noisy wrapper string exclusion.
 */
public class NormalizedMethod {
    public final MethodKey key;
    public final Set<String> stringConstants = new HashSet<>();
    public final Map<Integer, Integer> opcodeHistogram = new HashMap<>();
    public final Set<String> invokedSignatures = new HashSet<>();
    public final String normalizedDescriptor;
    public final String fingerprint;

    public NormalizedMethod(String ownerInternalName, MethodNode original) {
        this.key = new MethodKey(ownerInternalName, original.name, original.desc);

        // Clone to work on a copy
        MethodNode working = cloneShallowMethodNode(original);

        // Try to unwrap a whole-method obfuscation wrapper if present
        InsnList unwrapped = tryUnwrapWholeRuntimeExceptionWrapper(working);
        boolean didUnwrap = false;
        Set<String> wrapperNoisyStrings = Collections.emptySet();
        if (unwrapped != null) {
            working.instructions = unwrapped;
            working.tryCatchBlocks = new ArrayList<>();
            didUnwrap = true;
        } else {
            // If we didn't fully unwrap, collect the wrapper's signature string to exclude from constants
            wrapperNoisyStrings = collectWrapperSignatureStrings(working);
        }

        // Detect opaque predicate params (including trailing unused ones)
        //Set<Integer> opaqueParams = detectOpaquePredicateParams(working);
        Set<Integer> opaqueParams = Collections.emptySet();

        // Normalize descriptor (drops opaque / unused trailing params)
        this.normalizedDescriptor = normalizeDescriptor(original.desc, opaqueParams);

        // Build exclusion set for instructions
        Set<AbstractInsnNode> toExclude = new HashSet<>();
        toExclude.addAll(findOpaquePredicateGuardInsns(working, opaqueParams));
        if (!didUnwrap) {
            toExclude.addAll(findObfuscationExceptionWrapperInsns(working));
        }

        // Process instructions, passing noisy string exclusions
        processInstructions(working, toExclude, wrapperNoisyStrings);
        this.fingerprint = fingerprint();
    }

    private MethodNode cloneShallowMethodNode(MethodNode mn) {
        MethodNode clone = new MethodNode(
                mn.access,
                mn.name,
                mn.desc,
                mn.signature,
                mn.exceptions == null ? null : mn.exceptions.toArray(new String[0])
        );
        if (mn.instructions != null) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                clone.instructions.add(insn);
            }
        }
        if (mn.tryCatchBlocks != null) {
            clone.tryCatchBlocks = new ArrayList<>(mn.tryCatchBlocks);
        }
        return clone;
    }

    private void processInstructions(MethodNode mn, Set<AbstractInsnNode> excludeInsns, Set<String> excludeStrings) {
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (excludeInsns.contains(insn)) {
                continue;
            }

            int opcode = insn.getOpcode();
            if (opcode >= 0) {
                opcodeHistogram.merge(opcode, 1, Integer::sum);
            }

            if (insn instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) insn).cst;
                if (cst instanceof String) {
                    String s = (String) cst;
                    if (excludeStrings.contains(s)) {
                        continue; // drop noisy wrapper signature strings
                    }
                    stringConstants.add(s);
                }
            }

            if (insn instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                String sig = mi.owner + "." + mi.name + mi.desc;
                invokedSignatures.add(sig);
            }
        }
    }

    /**
     * Detects trailing unused parameters (plus existing opaque predicate heuristics).
     */
    private Set<Integer> detectOpaquePredicateParams(MethodNode mn) {
        Set<Integer> candidates = new HashSet<>();

        boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
        int paramBase = isStatic ? 0 : 1;
        Type[] args = Type.getArgumentTypes(mn.desc);
        int totalParams = args.length;

        // legacy/seeding: guard-style opaque predicate parameters
        for (int i = Math.max(0, totalParams - 2); i < totalParams; i++) {
            int localIndex = paramBase + computeLocalIndex(args, i);
            if (looksLikeOpaquePredicate(mn, localIndex)) {
                candidates.add(i);
            }
        }

        // New: strip trailing parameters that are never read (unused)
        for (int i = totalParams - 1; i >= 0; i--) {
            int localIndex = paramBase + computeLocalIndex(args, i);
            if (!isParameterUsed(mn, args[i], localIndex)) {
                candidates.add(i);
            } else {
                break; // stop at first used from the end
            }
        }

        return candidates;
    }

    private boolean isParameterUsed(MethodNode mn, Type type, int localIndex) {
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode v = (VarInsnNode) insn;
                if (v.var == localIndex) {
                    // check load or store depending on context; conservative: any reference counts as used
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * If a RuntimeException wrapper exists around whole method and matches the obfuscation pattern,
     * unwrap and return just the inner try body.
     */
    private InsnList tryUnwrapWholeRuntimeExceptionWrapper(MethodNode mn) {
        if (mn.tryCatchBlocks == null || mn.tryCatchBlocks.size() != 1) {
            return null;
        }

        TryCatchBlockNode tcb = mn.tryCatchBlocks.get(0);
        if (!"java/lang/RuntimeException".equals(tcb.type)) {
            return null;
        }

        if (!isObfuscationCatchWrapper(tcb, mn)) {
            return null;
        }

        // Collect instructions in the try region up to the core return/throw.
        List<AbstractInsnNode> inner = new ArrayList<>();
        AbstractInsnNode cur = tcb.start;
        boolean sawExit = false;
        while (cur != null && cur != tcb.end) {
            if (cur.getOpcode() >= 0) {
                inner.add(cur);
                if (isReturnOpcode(cur.getOpcode()) || cur.getOpcode() == Opcodes.ATHROW) {
                    sawExit = true;
                    // allow only trivial (labels/frames) after exit until tcb.end
                    AbstractInsnNode after = cur.getNext();
                    while (after != null && after != tcb.end) {
                        if (!isTrivial(after)) {
                            return null;
                        }
                        after = after.getNext();
                    }
                    break;
                }
            } else {
                inner.add(cur);
            }
            cur = cur.getNext();
        }

        if (!sawExit) {
            return null;
        }

        InsnList cleaned = new InsnList();
        for (AbstractInsnNode insn : inner) {
            cleaned.add(insn.clone(new HashMap<>())); // clone for safety
        }
        return cleaned;
    }

    private boolean isTrivial(AbstractInsnNode insn) {
        return insn.getOpcode() < 0; // frames / labels / line numbers
    }

    private boolean isReturnOpcode(int opcode) {
        return opcode == Opcodes.RETURN
                || opcode == Opcodes.IRETURN
                || opcode == Opcodes.ARETURN
                || opcode == Opcodes.LRETURN
                || opcode == Opcodes.DRETURN
                || opcode == Opcodes.FRETURN;
    }

    /**
     * Collects the noisy string constants from an obfuscation catch-wrapper (e.g., "sb.ao(" + ')')
     * so they can be excluded when fingerprinting.
     */
    private Set<String> collectWrapperSignatureStrings(MethodNode mn) {
        Set<String> out = new HashSet<>();
        if (mn.tryCatchBlocks == null) {
            return out;
        }

        for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
            if (!"java/lang/RuntimeException".equals(tcb.type)) continue;
            // Walk handler to find the LDC string in the wrapper pattern
            AbstractInsnNode cur = tcb.handler;

            // optional ASTORE
            if (cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ASTORE) {
                cur = cur.getNext();
            }

            // skip non-opcodes
            while (cur != null && cur.getOpcode() < 0) {
                cur = cur.getNext();
            }

            // expect ALOAD (exception)
            if (!(cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ALOAD)) continue;
            cur = cur.getNext();

            while (cur != null && cur.getOpcode() < 0) {
                cur = cur.getNext();
            }

            // expect LDC string
            if (cur instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) cur).cst;
                if (cst instanceof String) {
                    String s = (String) cst;
                    if (s.contains("(") && s.contains(")")) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    private Set<AbstractInsnNode> findOpaquePredicateGuardInsns(MethodNode mn, Set<Integer> opaqueParamIndexes) {
        Set<AbstractInsnNode> toExclude = new HashSet<>();
        boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
        int paramBase = isStatic ? 0 : 1;
        Type[] args = Type.getArgumentTypes(mn.desc);

        for (int paramIdx : opaqueParamIndexes) {
            int localIndex = paramBase + computeLocalIndex(args, paramIdx);
            AbstractInsnNode[] insns = mn.instructions.toArray();
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof VarInsnNode) {
                    VarInsnNode vin = (VarInsnNode) insn;
                    if (vin.getOpcode() == Opcodes.ILOAD && vin.var == localIndex) {
                        // binary comparison: ILOAD, CONST, IF_ICMPNE / IF_ICMPEQ
                        AbstractInsnNode next = vin.getNext();
                        if (next != null) {
                            Integer constVal = extractIntConstant(next);
                            if (constVal != null) {
                                AbstractInsnNode afterConst = next.getNext();
                                if (afterConst instanceof JumpInsnNode) {
                                    JumpInsnNode jmp = (JumpInsnNode) afterConst;
                                    if (jmp.getOpcode() == Opcodes.IF_ICMPNE || jmp.getOpcode() == Opcodes.IF_ICMPEQ) {
                                        Set<AbstractInsnNode> early = gatherEarlyExitBlock(jmp.label);
                                        if (!early.isEmpty()) {
                                            toExclude.add(vin);
                                            toExclude.add(next);
                                            toExclude.add(jmp);
                                            toExclude.addAll(early);
                                        }
                                    }
                                }
                            }
                        }

                        // unary compare: ILOAD, IFEQ/IFNE
                        AbstractInsnNode next1 = vin.getNext();
                        if (next1 instanceof JumpInsnNode) {
                            JumpInsnNode jmp = (JumpInsnNode) next1;
                            int opc = jmp.getOpcode();
                            if (opc == Opcodes.IFEQ || opc == Opcodes.IFNE) {
                                Set<AbstractInsnNode> early = gatherEarlyExitBlock(jmp.label);
                                if (!early.isEmpty()) {
                                    toExclude.add(vin);
                                    toExclude.add(jmp);
                                    toExclude.addAll(early);
                                }
                            }
                        }
                    }
                }
            }
        }

        return toExclude;
    }

    private Set<AbstractInsnNode> findObfuscationExceptionWrapperInsns(MethodNode mn) {
        Set<AbstractInsnNode> exclude = new HashSet<>();
        if (mn.tryCatchBlocks == null) return exclude;

        for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
            if (!"java/lang/RuntimeException".equals(tcb.type)) continue;
            if (!isObfuscationCatchWrapper(tcb, mn)) continue;

            // exclude handler region up through the ATHROW
            AbstractInsnNode cur = tcb.handler;
            while (cur != null) {
                exclude.add(cur);
                if (cur instanceof InsnNode && cur.getOpcode() == Opcodes.ATHROW) {
                    break;
                }
                cur = cur.getNext();
            }
        }
        return exclude;
    }

    /**
     * Rough detection of the standard obfuscation catcher (sb.ao / sp.ap style).
     */
    private boolean isObfuscationCatchWrapper(TryCatchBlockNode tcb, MethodNode mn) {
        AbstractInsnNode cur = tcb.handler;

        // optional ASTORE
        if (cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ASTORE) {
            cur = cur.getNext();
        }

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        // expect ALOAD
        if (!(cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ALOAD)) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        // expect LDC string with parentheses
        if (!(cur instanceof LdcInsnNode)) return false;
        Object cst = ((LdcInsnNode) cur).cst;
        if (!(cst instanceof String)) return false;
        String s = (String) cst;
        if (!s.contains("(") || !s.contains(")")) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        // expect helper invocation
        if (!(cur instanceof MethodInsnNode)) return false;
        MethodInsnNode mi = (MethodInsnNode) cur;
        if (mi.getOpcode() != Opcodes.INVOKESTATIC) return false;
        if (!"(Ljava/lang/Throwable;Ljava/lang/String;)Ljava/lang/Throwable;".equals(mi.desc)) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        // expect ATHROW
        return cur instanceof InsnNode && cur.getOpcode() == Opcodes.ATHROW;
    }

    private Set<Integer> computeOpaqueParamCandidatesFromUsage(MethodNode mn) {
        // not used directly here; usage-based (unused trailing) is in detectOpaquePredicateParams
        return Collections.emptySet();
    }

    private int computeLocalIndex(Type[] args, int targetParamIndex) {
        int idx = 0;
        for (int i = 0; i < targetParamIndex; i++) {
            idx += args[i].getSize();
        }
        return idx;
    }

    /**
     * Extracts an integer constant if the instruction pushes one, else null.
     */
    private Integer extractIntConstant(AbstractInsnNode insn) {
        if (insn == null) return null;

        int op = insn.getOpcode();
        switch (op) {
            case Opcodes.ICONST_M1:
                return -1;
            case Opcodes.ICONST_0:
                return 0;
            case Opcodes.ICONST_1:
                return 1;
            case Opcodes.ICONST_2:
                return 2;
            case Opcodes.ICONST_3:
                return 3;
            case Opcodes.ICONST_4:
                return 4;
            case Opcodes.ICONST_5:
                return 5;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                if (insn instanceof IntInsnNode) {
                    return ((IntInsnNode) insn).operand;
                }
                break;
            default:
                if (insn instanceof LdcInsnNode) {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Integer) {
                        return (Integer) cst;
                    }
                }
        }
        return null;
    }

    private String normalizeDescriptor(String desc, Set<Integer> opaqueParamIndexes) {
        if (opaqueParamIndexes.isEmpty()) {
            return desc;
        }
        Type returnType = Type.getReturnType(desc);
        Type[] args = Type.getArgumentTypes(desc);
        List<Type> filtered = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (!opaqueParamIndexes.contains(i)) {
                filtered.add(args[i]);
            }
        }
        Type[] newArgs = filtered.toArray(new Type[0]);
        return Type.getMethodDescriptor(returnType, newArgs);
    }

    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizedDescriptor).append("|");
        sb.append(sortedHash(opcodeHistogram.keySet())).append("|");
        sb.append(sortedHash(invokedSignatures)).append("|");
        sb.append(sortedHash(stringConstants)).append("|");
        return sha256(sb.toString());
    }

    private static String sortedHash(Collection<?> col) {
        List<String> list = new ArrayList<>();
        for (Object o : col) {
            list.add(String.valueOf(o));
        }
        Collections.sort(list);
        return String.join(",", list);
    }

    private static String sha256(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(in.hashCode());
        }
    }

    /**
     * Looks ahead from `start` and, if within a small window there is an early exit
     * (return or throw), returns the instructions from start up through that exit.
     * Otherwise returns an empty set.
     */
    private Set<AbstractInsnNode> gatherEarlyExitBlock(AbstractInsnNode start) {
        if (start == null) {
            return Collections.emptySet();
        }

        AbstractInsnNode cur = start;
        int steps = 0;
        List<AbstractInsnNode> collected = new ArrayList<>();
        while (cur != null && steps < 12) {
            if (cur.getOpcode() >= 0) {
                collected.add(cur);
                int op = cur.getOpcode();
                if (isReturnOpcode(op) || op == Opcodes.ATHROW) {
                    return new HashSet<>(collected);
                }
            } else {
                // labels / frames / line numbers
                collected.add(cur);
            }
            cur = cur.getNext();
            steps++;
        }
        return Collections.emptySet();
    }

    /**
     * Simple heuristic to seed opaque-predicate detection.
     * Checks for pattern like:
     *   ILOAD varIndex
     *   (optional) constant push
     *   conditional jump
     * where the jump target (failure path) immediately contains a return/throw.
     */
    private boolean looksLikeOpaquePredicate(MethodNode mn, int varIndex) {
        AbstractInsnNode[] insns = mn.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            AbstractInsnNode insn = insns[i];
            if (insn instanceof VarInsnNode) {
                VarInsnNode vin = (VarInsnNode) insn;
                if ((vin.getOpcode() == Opcodes.ILOAD) && vin.var == varIndex) {
                    // Case 1: ILOAD, CONST, IF_ICMPNE / IF_ICMPEQ
                    AbstractInsnNode next = vin.getNext();
                    if (next != null) {
                        Integer constVal = extractIntConstant(next);
                        if (constVal != null) {
                            AbstractInsnNode afterConst = next.getNext();
                            if (afterConst instanceof JumpInsnNode) {
                                JumpInsnNode jmp = (JumpInsnNode) afterConst;
                                if (jmp.getOpcode() == Opcodes.IF_ICMPNE || jmp.getOpcode() == Opcodes.IF_ICMPEQ) {
                                    Set<AbstractInsnNode> early = gatherEarlyExitBlock(jmp.label);
                                    if (!early.isEmpty()) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }

                    // Case 2: unary compare: ILOAD, IFEQ/IFNE
                    AbstractInsnNode next1 = vin.getNext();
                    if (next1 instanceof JumpInsnNode) {
                        JumpInsnNode jmp = (JumpInsnNode) next1;
                        int opc = jmp.getOpcode();
                        if (opc == Opcodes.IFEQ || opc == Opcodes.IFNE) {
                            Set<AbstractInsnNode> early = gatherEarlyExitBlock(jmp.label);
                            if (!early.isEmpty()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
