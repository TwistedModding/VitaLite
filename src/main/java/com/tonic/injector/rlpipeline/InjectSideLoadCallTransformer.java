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
        if(!classNode.name.equals("net/runelite/client/RuneLite"))
            return;

        MethodNode main = classNode.methods.stream()
                .filter(method -> method.name.equals("start") && method.desc.equals("()V"))
                .findFirst()
                .orElse(null);

        if(main == null)
        {
            System.out.println("Failed to find RuneLite start method");
            return;
        }

        AbstractInsnNode insertionPoint = null;

        for (AbstractInsnNode insn : main.instructions) {
            if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode m = (MethodInsnNode) insn;
                if (m.owner.equals("net/runelite/client/plugins/PluginManager")
                        && m.name.equals("loadSideLoadPlugins")
                        && m.desc.equals("()V")) {
                    insertionPoint = insn.getNext();
                    break;
                }
            }
        }
        if (insertionPoint == null) {
            System.out.println("Couldn’t locate RuneLite.start() call");
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

                // RuneLite.injector.getInstance(Install.class).start(RUNELITE);
                .getStaticField("net/runelite/client/RuneLite",
                        "injector",
                        "Lcom/google/inject/Injector;")
                .pushClass("com/tonic/runelite/Install")
                .invokeInterface("com/google/inject/Injector",
                        "getInstance",
                        "(Ljava/lang/Class;)Ljava/lang/Object;")
                .castToType("com/tonic/runelite/Install")
                .invokeStatic("com/tonic/Static",
                        "getRuneLite",
                        "()Lcom/tonic/model/RuneLite;")
                .invokeVirtual("com/tonic/runelite/Install",
                        "start",
                        "(Lcom/tonic/model/RuneLite;)V")

                // RuneLite.injector.getInstance(net.runelite.client.eventbus.EventBus.class)
                //      .post(new net.runelite.client.events.ExternalPluginsChanged());
                .pushThis()
                .getField("net/runelite/client/RuneLite",
                        "eventBus",
                        "Lnet/runelite/client/eventbus/EventBus;")
                .newInstance("net/runelite/client/events/ExternalPluginsChanged")
                .dup()
                .invokeSpecial(
                        "net/runelite/client/events/ExternalPluginsChanged",
                        "<init>",
                        "()V"
                )
                .castToType("java/lang/Object")
                .invokeVirtual("net/runelite/client/eventbus/EventBus",
                        "post",
                        "(Ljava/lang/Object;)V")
                .build();


        main.instructions.insert(insertionPoint, code);
        System.out.println("Injected side load call into RuneLite main method");
    }
}
