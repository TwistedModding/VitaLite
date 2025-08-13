package com.tonic.injector.rlpipeline;

import com.tonic.util.BytecodeBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * This transformer patches the PluginManager class to always return true for the developerMode check
 * so that it always loads the devtools plugins.
 */
public class PatchDevToolsPluginManagerTransformer
{
    public static void patch(ClassNode classNode) {
        if (!classNode.name.equals("net/runelite/client/plugins/PluginManager"))
            return;

        MethodNode main = classNode.methods.stream()
                .filter(method -> method.name.equals("loadPlugins"))
                .findFirst()
                .orElse(null);

        if (main == null) {
            System.out.println("Failed to find loadPlugins method in PluginManager");
            return;
        }

        AbstractInsnNode insertionPoint = null;
        for (AbstractInsnNode insn : main.instructions) {
            if (insn.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode m = (FieldInsnNode) insn;
                if (m.owner.equals("net/runelite/client/plugins/PluginManager")
                        && m.name.equals("developerMode")
                        && m.desc.equals("Z")) {
                    insertionPoint = insn;
                    break;
                }
            }
        }
        if (insertionPoint == null) {
            System.out.println("Couldn’t locate RuneLite.main() call");
            return;
        }

        InsnList code = BytecodeBuilder.create()
                .pushInt(1)
                .build();

        main.instructions.insert(insertionPoint, code);
        main.instructions.remove(insertionPoint.getPrevious());
        main.instructions.remove(insertionPoint);
        System.out.println("Patched PluginManager.developerMode check in PluginManager.loadPlugins()");
    }
}
