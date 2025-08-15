package com.tonic.injector.rlpipeline;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * This class patches the RuneLiteModule class to disable telemetry by making the
 * provideTelemetry method return null.
 */
public class DisableTelemetryTransformer {
    public static void patch(ClassNode classNode) {
        if (!classNode.name.equals("net/runelite/client/RuneLiteModule"))
            return;

        MethodNode provideTelemetry = classNode.methods.stream()
                .filter(method -> method.name.equals("provideTelemetry"))
                .findFirst()
                .orElse(null);

        if (provideTelemetry == null) {
            System.out.println("Failed to find RuneLite provideTelemetry method");
            return;
        }

        // Remove the method body to make it a no-op
        provideTelemetry.instructions.clear();
        provideTelemetry.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); // push null
        provideTelemetry.instructions.add(new InsnNode(Opcodes.ARETURN));
        provideTelemetry.tryCatchBlocks.clear();
        provideTelemetry.localVariables.clear();
        provideTelemetry.maxStack = 1;
        provideTelemetry.maxLocals = 0;

        System.out.println("Patched RuneLiteModule.provideTelemetry to always return null");
    }
}
