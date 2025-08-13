package com.tonic.injector.rlpipeline;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * This transformer modifies the PluginManager class to make the loadSideLoadPlugins method a no-op. Solves
 * issues with dev mode attempting to double load our plugins from the side-loaded plugins directory.
 */
public class NoOpLoadSideLoadPluginsTransformer
{
    public static void patch(ClassNode classNode){
        if(!classNode.name.equals("net/runelite/client/plugins/PluginManager"))
            return;

        MethodNode loadPluginsMethod = classNode.methods.stream()
                .filter(method -> method.name.equals("loadSideLoadPlugins") && method.desc.equals("()V"))
                .findFirst()
                .orElse(null);

        if(loadPluginsMethod == null)
        {
            System.out.println("Failed to find PluginManager.loadSideLoadPlugins method");
            return;
        }

        // Remove the method body to make it a no-op
        loadPluginsMethod.instructions.clear();
        loadPluginsMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        loadPluginsMethod.tryCatchBlocks.clear();
        loadPluginsMethod.localVariables.clear();
        loadPluginsMethod.maxStack = 0;
        loadPluginsMethod.maxLocals = 0;
    }
}
