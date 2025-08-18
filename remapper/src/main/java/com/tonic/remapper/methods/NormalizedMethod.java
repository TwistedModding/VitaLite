package com.tonic.remapper.methods;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Extracts simplified features from a MethodNode for matching
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

        MethodNode working = cloneShallowMethodNode(original);

        InsnList unwrapped = tryUnwrapWholeRuntimeExceptionWrapper(working);
        boolean didUnwrap = false;
        Set<String> wrapperNoisyStrings = Collections.emptySet();
        if (unwrapped != null) {
            working.instructions = unwrapped;
            working.tryCatchBlocks = new ArrayList<>();
            didUnwrap = true;
        } else {
            wrapperNoisyStrings = collectWrapperSignatureStrings(working);
        }

        Set<Integer> opaqueParams = Collections.emptySet();

        this.normalizedDescriptor = normalizeDescriptor(original.desc, opaqueParams);

        Set<AbstractInsnNode> toExclude = new HashSet<>(findOpaquePredicateGuardInsns(working, opaqueParams));
        if (!didUnwrap) {
            toExclude.addAll(findObfuscationExceptionWrapperInsns(working));
        }

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
                        continue;
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

        List<AbstractInsnNode> inner = new ArrayList<>();
        AbstractInsnNode cur = tcb.start;
        boolean sawExit = false;
        while (cur != null && cur != tcb.end) {
            if (cur.getOpcode() >= 0) {
                inner.add(cur);
                if (isReturnOpcode(cur.getOpcode()) || cur.getOpcode() == Opcodes.ATHROW) {
                    sawExit = true;
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
            cleaned.add(insn.clone(new HashMap<>()));
        }
        return cleaned;
    }

    private boolean isTrivial(AbstractInsnNode insn) {
        return insn.getOpcode() < 0;
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
     * Collects the noisy string constants, so they can be excluded when fingerprinting.
     */
    private Set<String> collectWrapperSignatureStrings(MethodNode mn) {
        Set<String> out = new HashSet<>();
        if (mn.tryCatchBlocks == null) {
            return out;
        }

        for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
            if (!"java/lang/RuntimeException".equals(tcb.type)) continue;
            AbstractInsnNode cur = tcb.handler;

            if (cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ASTORE) {
                cur = cur.getNext();
            }

            while (cur != null && cur.getOpcode() < 0) {
                cur = cur.getNext();
            }

            if (!(cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ALOAD)) continue;
            cur = cur.getNext();

            while (cur != null && cur.getOpcode() < 0) {
                cur = cur.getNext();
            }

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

        if (cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ASTORE) {
            cur = cur.getNext();
        }

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        if (!(cur instanceof VarInsnNode && cur.getOpcode() == Opcodes.ALOAD)) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        if (!(cur instanceof LdcInsnNode)) return false;
        Object cst = ((LdcInsnNode) cur).cst;
        if (!(cst instanceof String)) return false;
        String s = (String) cst;
        if (!s.contains("(") || !s.contains(")")) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        if (!(cur instanceof MethodInsnNode)) return false;
        MethodInsnNode mi = (MethodInsnNode) cur;
        if (mi.getOpcode() != Opcodes.INVOKESTATIC) return false;
        if (!"(Ljava/lang/Throwable;Ljava/lang/String;)Ljava/lang/Throwable;".equals(mi.desc)) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) {
            cur = cur.getNext();
        }

        return cur instanceof InsnNode && cur.getOpcode() == Opcodes.ATHROW;
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
                collected.add(cur);
            }
            cur = cur.getNext();
            steps++;
        }
        return Collections.emptySet();
    }
}
