package com.tonic.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public class FieldUsageExtractor {
    /**
     * Builds reader and writer sets for each field based on provided methods.
     */
    public static class FieldUsage {
        public final Map<FieldKey, Set<MethodKey>> readers = new HashMap<>();
        public final Map<FieldKey, Set<MethodKey>> writers = new HashMap<>();
    }

    public static FieldUsage extractFieldUsage(Map<MethodKey, MethodNode> methods) {
        FieldUsage usage = new FieldUsage();

        for (Map.Entry<MethodKey, MethodNode> entry : methods.entrySet()) {
            MethodKey methodKey = entry.getKey();
            MethodNode mn = entry.getValue();

            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    FieldKey fieldKey = new FieldKey(fin.owner, fin.name, fin.desc);

                    boolean isRead = fin.getOpcode() == Opcodes.GETFIELD || fin.getOpcode() == Opcodes.GETSTATIC;
                    boolean isWrite = fin.getOpcode() == Opcodes.PUTFIELD || fin.getOpcode() == Opcodes.PUTSTATIC;

                    if (isRead) {
                        usage.readers.computeIfAbsent(fieldKey, k -> new HashSet<>()).add(methodKey);
                    }
                    if (isWrite) {
                        usage.writers.computeIfAbsent(fieldKey, k -> new HashSet<>()).add(methodKey);
                    }
                }
            }
        }

        return usage;
    }
}
