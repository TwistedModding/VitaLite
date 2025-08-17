package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.dto.JMethod;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.MethodOverride;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.util.AnnotationUtil;
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
     *
     * @param gamepack The ClassNode of the gamepack being transformed.
     * @param mixin The ClassNode containing the mixin method.
     * @param method The MethodNode from the mixin that contains the new bytecode.
     */
    public static void patch(ClassNode gamepack, ClassNode mixin, MethodNode method) {
        InjectTransformer.patch(gamepack, mixin, method);

        String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
        String name = AnnotationUtil.getAnnotation(method, MethodOverride.class, "value");
        JClass jClass = MappingProvider.getClass(gamepackName);
        JMethod jMethod = MappingProvider.getMethod(jClass, name);

        String targetName = jMethod.getObfuscatedName();
        String targetDesc = jMethod.getDescriptor();

        MethodNode toReplace = gamepack.methods.stream()
                .filter(m -> m.name.equals(targetName) && m.desc.equals(targetDesc))
                .findFirst().orElse(null);

        if (toReplace == null) {
            System.err.println("[MethodOverrideTransformer] Could not find target to replace: "
                    + targetName + targetDesc);
            return;
        }

        toReplace.instructions.clear();
        toReplace.tryCatchBlocks.clear();
        toReplace.localVariables.clear();

        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LabelNode) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            AbstractInsnNode cloned = insn.clone(labelMap);
            if (cloned != null) {
                toReplace.instructions.add(cloned);
            } else {
                System.err.println("[MethodOverrideTransformer] Warning: Failed to clone instruction: " + insn);
            }
        }

        toReplace.maxStack  = method.maxStack;
        toReplace.maxLocals = method.maxLocals;
    }
}