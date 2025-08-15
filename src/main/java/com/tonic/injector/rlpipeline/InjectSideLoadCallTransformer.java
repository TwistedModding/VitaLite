package com.tonic.injector.rlpipeline;

import com.tonic.util.BytecodeBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * This transformer injects a call to the side load plugin installation process
 * into the RuneLite main method. The injection occurs after the call to
 * PluginManager.loadSideLoadPlugins().
 */
public class InjectSideLoadCallTransformer
{
    public static void patch(ClassNode classNode) {
        if(!classNode.name.equals("net/runelite/client/plugins/PluginManager"))
            return;

        MethodNode main = classNode.methods.stream()
                .filter(method -> method.name.equals("loadCorePlugins") && method.desc.equals("()V"))
                .findFirst()
                .orElse(null);

        if(main == null)
        {
            System.out.println("Failed to find RuneLite start method");
            return;
        }

        AbstractInsnNode insertionPoint = null;

        for (AbstractInsnNode insn : main.instructions) {

            if (insn.getOpcode() == Opcodes.ASTORE && ((VarInsnNode)insn).var == 2) {
                insertionPoint = insn.getNext();
            }
        }

        if (insertionPoint == null) {
            System.out.println("Couldn’t locate loadCorePlugins() call (2)");
            return;
        }


        InsnList code = BytecodeBuilder.create()
                // Static.set(new RuneLite(Main.CLASSLOADER.getMain()), "RL");
                .newInstance("com/tonic/model/RuneLite")
                .dup()
                .getStaticField(
                        "com/tonic/Main",
                        "CLASSLOADER",
                        "Lcom/tonic/classloader/RLClassLoader;"
                )
                .invokeVirtual(
                        "com/tonic/classloader/RLClassLoader",
                        "getMain",
                        "()Ljava/lang/Class;"
                )
                .invokeSpecial(
                        "com/tonic/model/RuneLite",
                        "<init>",
                        "(Ljava/lang/Class;)V"
                )
                .pushString("RL")
                .invokeStatic(
                        "com/tonic/Static",
                        "set",
                        "(Ljava/lang/Object;Ljava/lang/String;)V"
                )

                // RuneLite.injector.getInstance(Install.class).start(plugins);
                .invokeStatic("com/tonic/Static",
                        "getInjector",
                        "()Lcom/google/inject/Injector;")
                .pushClass("com/tonic/runelite/Install")
                .invokeInterface("com/google/inject/Injector",
                        "getInstance",
                        "(Ljava/lang/Class;)Ljava/lang/Object;")
                .castToType("com/tonic/runelite/Install")
                .loadLocal(2, Opcodes.ALOAD)
                .invokeVirtual("com/tonic/runelite/Install",
                        "injectSideLoadPlugins",
                        "(Ljava/util/List;)V")
                .build();


        main.instructions.insert(insertionPoint, code);
        System.out.println("Injected side load call into RuneLite main method");
    }
}
