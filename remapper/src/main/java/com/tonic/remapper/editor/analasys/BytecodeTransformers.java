package com.tonic.remapper.editor.analasys;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class BytecodeTransformers {

    // Map to track methods that had their last parameter removed
    private static final Map<String, MethodSignatureChange> modifiedMethods = new HashMap<>();

    public static class MethodSignatureChange {
        public final String owner;
        public final String name;
        public final String oldDesc;
        public final String newDesc;
        public final Type removedType;

        public MethodSignatureChange(String owner, String name, String oldDesc, String newDesc, Type removedType) {
            this.owner = owner;
            this.name = name;
            this.oldDesc = oldDesc;
            this.newDesc = newDesc;
            this.removedType = removedType;
        }

        public String getKey() {
            return owner + "." + name + oldDesc;
        }
    }

    public static DeobPipeline.MethodTransformer removeOpaquePredicateParameter(String className) {
        return mn -> {
            // Keep the 2-character name check - specific to this obfuscation
            if(mn.name.length() != 2)
                return;

            // Skip methods with no parameters
            Type methodType = Type.getMethodType(mn.desc);
            Type[] argTypes = methodType.getArgumentTypes();
            if (argTypes.length == 0) {
                return;
            }

            // Calculate the local variable index of the last parameter
            int lastParamIndex = calculateLastParamIndex(mn, argTypes);

            // Simple check: does this method contain InvalidStateException?
            boolean hasInvalidStateException = false;
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.NEW) {
                    TypeInsnNode tin = (TypeInsnNode) insn;
                    if (tin.desc.equals("java/lang/IllegalStateException") ||
                            tin.desc.contains("InvalidStateException")) {
                        hasInvalidStateException = true;
                        break;
                    }
                }
            }

            // Check if parameter is actually used (excluding opaque uses)
            boolean parameterUsedMeaningfully = false;
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (isLoadingParameter(insn, lastParamIndex)) {
                    // Check what happens after the load
                    if (!isOpaqueUse(insn)) {
                        parameterUsedMeaningfully = true;
                        break;
                    }
                }
            }

            // Only remove if we have InvalidStateException OR parameter is completely unused/only used in opaques
            if (!hasInvalidStateException && parameterUsedMeaningfully) {
                return;
            }

            // Store the original descriptor before modification
            String originalDesc = mn.desc;
            Type removedType = argTypes[argTypes.length - 1];

            // Remove the last parameter from the method descriptor
            String newDesc = removeLastParameter(mn.desc);
            mn.desc = newDesc;

            // Track this method modification
            String key = className + "." + mn.name + originalDesc;
            MethodSignatureChange change = new MethodSignatureChange(
                    className, mn.name, originalDesc, newDesc, removedType
            );
            modifiedMethods.put(key, change);

            // Now carefully remove opaque predicates and exception throws
            removeOpaquePredicatesCarefully(mn, lastParamIndex);

            // Update max locals
            mn.maxLocals = Math.max(mn.maxLocals - getParameterSize(removedType), 0);

            System.out.println("Removed opaque parameter from: " + className + "." + mn.name + originalDesc + " -> " + newDesc);
        };
    }

    private static boolean isOpaqueUse(AbstractInsnNode loadInsn) {
        // Check if this load is immediately followed by a comparison and jump
        AbstractInsnNode current = loadInsn.getNext();
        int count = 0;

        while (current != null && count < 10) {
            int opcode = current.getOpcode();

            // If we see a conditional jump, this is likely an opaque predicate
            if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) {
                return true;
            }

            // If we see a method call, field access, or store, it's a real use
            if ((opcode >= Opcodes.INVOKEVIRTUAL && opcode <= Opcodes.INVOKEDYNAMIC) ||
                    (opcode >= Opcodes.GETFIELD && opcode <= Opcodes.PUTSTATIC) ||
                    (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) ||
                    (opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE)) {
                return false;
            }

            // Skip metadata
            if (!(current instanceof LabelNode || current instanceof LineNumberNode ||
                    current instanceof FrameNode)) {
                count++;
            }

            current = current.getNext();
        }

        return true; // If we don't find a real use, assume it's opaque
    }

    private static void removeOpaquePredicatesCarefully(MethodNode mn, int paramIndex) {
        // Collect all labels that are jump targets - we must NOT remove these
        Set<LabelNode> referencedLabels = new HashSet<>();
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof JumpInsnNode) {
                referencedLabels.add(((JumpInsnNode) insn).label);
            } else if (insn instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode tsin = (TableSwitchInsnNode) insn;
                referencedLabels.add(tsin.dflt);
                referencedLabels.addAll(tsin.labels);
            } else if (insn instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) insn;
                referencedLabels.add(lsin.dflt);
                referencedLabels.addAll(lsin.labels);
            }
        }

        // Also collect labels used in try-catch blocks
        for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
            referencedLabels.add(tcb.start);
            referencedLabels.add(tcb.end);
            referencedLabels.add(tcb.handler);
        }

        List<AbstractInsnNode> toRemove = new ArrayList<>();

        // Pass 1: Remove parameter loads and their associated conditionals
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isLoadingParameter(insn, paramIndex)) {
                VarInsnNode load = (VarInsnNode) insn;

                // Find what this load is used for
                JumpInsnNode conditionalJump = findConditionalJump(load);
                if (conditionalJump != null) {
                    // Remove the load and everything up to (and including) the conditional jump
                    AbstractInsnNode current = load;
                    while (current != null && current != conditionalJump.getNext()) {
                        // Don't remove labels that are referenced elsewhere
                        if (!(current instanceof LabelNode && referencedLabels.contains(current))) {
                            toRemove.add(current);
                        }
                        current = current.getNext();
                    }

                    // Now handle the branches - replace with the simpler branch
                    simplifyBranches(mn, conditionalJump, toRemove, referencedLabels);
                } else {
                    // Just remove the load if it's not used in a conditional
                    toRemove.add(load);
                }
            }
        }

        // Pass 2: Remove InvalidStateException throws
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.NEW) {
                TypeInsnNode tin = (TypeInsnNode) insn;
                if (tin.desc.equals("java/lang/IllegalStateException") ||
                        tin.desc.contains("InvalidStateException")) {
                    // Remove the entire exception construction and throw
                    removeExceptionThrow(insn, toRemove, referencedLabels);
                }
            }
        }

        // Remove all marked instructions
        for (AbstractInsnNode insn : toRemove) {
            mn.instructions.remove(insn);
        }

        // Final cleanup - remove any NOPs or redundant jumps
        cleanupMethod(mn);
    }

    private static JumpInsnNode findConditionalJump(VarInsnNode load) {
        AbstractInsnNode current = load.getNext();
        int depth = 0;

        while (current != null && depth < 10) {
            if (current instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) current;
                if (jump.getOpcode() >= Opcodes.IFEQ && jump.getOpcode() <= Opcodes.IF_ACMPNE) {
                    return jump;
                }
                return null; // GOTO or other non-conditional jump
            }

            // If we hit a store or method call, the value is consumed elsewhere
            int opcode = current.getOpcode();
            if ((opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) ||
                    (opcode >= Opcodes.INVOKEVIRTUAL && opcode <= Opcodes.INVOKEDYNAMIC)) {
                return null;
            }

            if (!(current instanceof LabelNode || current instanceof LineNumberNode ||
                    current instanceof FrameNode)) {
                depth++;
            }

            current = current.getNext();
        }

        return null;
    }

    private static void simplifyBranches(MethodNode mn, JumpInsnNode conditionalJump,
                                         List<AbstractInsnNode> toRemove, Set<LabelNode> referencedLabels) {
        // Check what each branch does
        boolean fallThroughThrows = branchThrows(conditionalJump.getNext(), conditionalJump.label);
        boolean targetThrows = branchThrows(conditionalJump.label, null);

        if (fallThroughThrows && targetThrows) {
            // Both branches throw - we can remove both
            // But we need to leave one path (usually just a return or a simple throw)

            // Remove fall-through branch
            AbstractInsnNode current = conditionalJump.getNext();
            while (current != null && current != conditionalJump.label) {
                if (!(current instanceof LabelNode && referencedLabels.contains(current))) {
                    toRemove.add(current);
                }
                current = current.getNext();
            }

            // Remove target branch exception throw
            current = conditionalJump.label.getNext();
            while (current != null) {
                if (current.getOpcode() == Opcodes.ATHROW) {
                    toRemove.add(current);
                    break;
                }
                if (current.getOpcode() == Opcodes.NEW) {
                    TypeInsnNode tin = (TypeInsnNode) current;
                    if (tin.desc.contains("Exception")) {
                        // Remove this exception construction
                        removeExceptionThrow(current, toRemove, referencedLabels);
                        break;
                    }
                }
                if (current instanceof LabelNode && current != conditionalJump.label) {
                    break; // Reached another block
                }
                current = current.getNext();
            }

            // Add a simple return if needed
            if (needsReturn(mn)) {
                InsnNode returnInsn = new InsnNode(getReturnOpcode(mn));
                mn.instructions.insert(conditionalJump.label, returnInsn);
            }

        } else if (fallThroughThrows || targetThrows) {
            // Only one branch throws - keep the other branch
            // The conditional jump removal will cause execution to fall through to the non-throwing branch
        }
    }

    private static boolean branchThrows(AbstractInsnNode start, AbstractInsnNode end) {
        AbstractInsnNode current = start;
        while (current != null && current != end) {
            int opcode = current.getOpcode();

            if (opcode == Opcodes.NEW) {
                TypeInsnNode tin = (TypeInsnNode) current;
                if (tin.desc.contains("Exception") || tin.desc.contains("Error")) {
                    return true;
                }
            }

            if (opcode == Opcodes.ATHROW) {
                return true;
            }

            // Check for early return
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                return true; // Treat early returns as "throwing" for simplification
            }

            // If we hit another label (excluding our start), we've left this branch
            if (current != start && current instanceof LabelNode) {
                AbstractInsnNode next = current.getNext();
                if (next != null && !(next instanceof LineNumberNode || next instanceof FrameNode)) {
                    break;
                }
            }

            current = current.getNext();
        }
        return false;
    }

    private static void removeExceptionThrow(AbstractInsnNode newInsn, List<AbstractInsnNode> toRemove,
                                             Set<LabelNode> referencedLabels) {
        // Find the start of this exception construction
        AbstractInsnNode current = newInsn;

        // Remove: NEW, DUP, LDC (message), INVOKESPECIAL <init>, ATHROW
        while (current != null) {
            // Don't remove referenced labels
            if (!(current instanceof LabelNode && referencedLabels.contains(current))) {
                toRemove.add(current);
            }

            if (current.getOpcode() == Opcodes.ATHROW) {
                break; // Found the end
            }

            // Safety check - don't go too far
            if (current != newInsn) {
                AbstractInsnNode next = current.getNext();
                if (next instanceof LabelNode ||
                        (next != null && next.getOpcode() >= Opcodes.IRETURN && next.getOpcode() <= Opcodes.RETURN)) {
                    break;
                }
            }

            current = current.getNext();
        }
    }

    private static boolean needsReturn(MethodNode mn) {
        // Check if the method needs a return statement
        Type returnType = Type.getMethodType(mn.desc).getReturnType();

        // Check if there's already a return at the end
        AbstractInsnNode last = mn.instructions.getLast();
        while (last != null && (last instanceof LabelNode || last instanceof LineNumberNode ||
                last instanceof FrameNode)) {
            last = last.getPrevious();
        }

        if (last != null && last.getOpcode() >= Opcodes.IRETURN && last.getOpcode() <= Opcodes.RETURN) {
            return false; // Already has a return
        }

        return true;
    }

    private static int getReturnOpcode(MethodNode mn) {
        Type returnType = Type.getMethodType(mn.desc).getReturnType();
        switch (returnType.getSort()) {
            case Type.VOID: return Opcodes.RETURN;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT: return Opcodes.IRETURN;
            case Type.FLOAT: return Opcodes.FRETURN;
            case Type.LONG: return Opcodes.LRETURN;
            case Type.DOUBLE: return Opcodes.DRETURN;
            default: return Opcodes.ARETURN;
        }
    }

    private static void cleanupMethod(MethodNode mn) {
        boolean changed = true;
        while (changed) {
            changed = false;

            AbstractInsnNode current = mn.instructions.getFirst();
            while (current != null) {
                AbstractInsnNode next = current.getNext();

                // Remove GOTO that jumps to the next instruction
                if (current instanceof JumpInsnNode) {
                    JumpInsnNode jump = (JumpInsnNode) current;
                    if (jump.getOpcode() == Opcodes.GOTO) {
                        // Find the next real instruction
                        AbstractInsnNode realNext = next;
                        while (realNext != null && (realNext instanceof LabelNode ||
                                realNext instanceof LineNumberNode || realNext instanceof FrameNode)) {
                            if (realNext == jump.label) {
                                // This GOTO jumps to the next instruction, remove it
                                mn.instructions.remove(jump);
                                changed = true;
                                break;
                            }
                            realNext = realNext.getNext();
                        }
                    }
                }

                // Remove duplicate returns
                if (current.getOpcode() >= Opcodes.IRETURN && current.getOpcode() <= Opcodes.RETURN) {
                    if (next != null && next.getOpcode() == current.getOpcode()) {
                        mn.instructions.remove(next);
                        changed = true;
                    }
                }

                current = next;
            }
        }
    }

    // Utility methods
    private static int calculateLastParamIndex(MethodNode mn, Type[] argTypes) {
        int index = (mn.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0; // Account for 'this'
        for (int i = 0; i < argTypes.length - 1; i++) {
            index += argTypes[i].getSize();
        }
        return index;
    }

    private static int getParameterSize(Type type) {
        return (type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE) ? 2 : 1;
    }

    private static boolean isLoadingParameter(AbstractInsnNode insn, int paramIndex) {
        if (insn instanceof VarInsnNode) {
            VarInsnNode vin = (VarInsnNode) insn;
            return (vin.getOpcode() >= Opcodes.ILOAD && vin.getOpcode() <= Opcodes.ALOAD)
                    && vin.var == paramIndex;
        }
        return false;
    }

    private static String removeLastParameter(String desc) {
        Type methodType = Type.getMethodType(desc);
        Type[] argTypes = methodType.getArgumentTypes();
        Type returnType = methodType.getReturnType();

        if (argTypes.length == 0) {
            return desc;
        }

        Type[] newArgTypes = Arrays.copyOf(argTypes, argTypes.length - 1);
        return Type.getMethodDescriptor(returnType, newArgTypes);
    }

    public static Map<String, MethodSignatureChange> getModifiedMethods() {
        return Collections.unmodifiableMap(modifiedMethods);
    }

    public static void clearModifiedMethods() {
        modifiedMethods.clear();
    }

    // Keep your original stripTryCatch method unchanged
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
        while (insn != null && count < 10) {
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
}