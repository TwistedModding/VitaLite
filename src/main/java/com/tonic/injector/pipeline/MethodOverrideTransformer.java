package com.tonic.injector.pipeline;

import com.tonic.injector.annotations.MethodOverride;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import java.util.HashMap;
import java.util.Map;

public class MethodOverrideTransformer {
    /**
     * Replaces the bytecode of the target (toHook) method in the gamepack with the bytecode
     * from the provided mixin MethodNode.
     */
    public static void patch(ClassNode gamepack, ClassNode mixin, MethodNode method) {
        // Ensure the mixin method exists in the gamepack
        InjectTransformer.patch(gamepack, mixin, method);

        // Read target identity from @MethodOverride on the mixin method
        String targetName = AnnotationUtil.getAnnotation(method, MethodOverride.class, "name");
        String targetDesc = AnnotationUtil.getAnnotation(method, MethodOverride.class, "desc");

        // Find the target method in the gamepack
        MethodNode toReplace = gamepack.methods.stream()
                .filter(m -> m.name.equals(targetName) && m.desc.equals(targetDesc))
                .findFirst().orElse(null);

        if (toReplace == null) {
            System.err.println("[MethodOverrideTransformer] Could not find target to replace: "
                    + targetName + targetDesc);
            return;
        }

        // Wipe out original instructions and metadata
        toReplace.instructions.clear();
        toReplace.tryCatchBlocks.clear();
        toReplace.localVariables.clear();

        // Prepare a label mapping for cloning labels correctly
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();

        // First pass: create all label mappings
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LabelNode) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }

        // Clone and insert all instructions from the mixin method
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            AbstractInsnNode cloned = insn.clone(labelMap);
            if (cloned != null) {
                toReplace.instructions.add(cloned);
            } else {
                System.err.println("[MethodOverrideTransformer] Warning: Failed to clone instruction: " + insn);
            }
        }

        // Copy max stack and locals to match the mixin method
        toReplace.maxStack  = method.maxStack;
        toReplace.maxLocals = method.maxLocals;
    }
}