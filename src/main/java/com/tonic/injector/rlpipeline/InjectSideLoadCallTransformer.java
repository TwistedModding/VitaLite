package com.tonic.injector.rlpipeline;

import com.tonic.util.BytecodeBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

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
            System.out.println("Failed to find RuneLite main method");
            return;
        }

        AbstractInsnNode insertionPoint = null;

        for (AbstractInsnNode insn : main.instructions) {
            if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode m = (MethodInsnNode) insn;
                if (m.owner.equals("net/runelite/client/ui/ClientUI")
                        && m.name.equals("init")
                        && m.desc.equals("()V")) {
                    insertionPoint = insn;
                    break;
                }
            }
        }
        if (insertionPoint == null) {
            System.out.println("Couldn’t locate RuneLite.start() call");
            return;
        }


        InsnList code = BytecodeBuilder.create()
                // Main.setRUNELITE(new RuneLite())
                .newInstance("com/tonic/runelite/model/RuneLite")
                .dup()
                .invokeSpecial(
                        "com/tonic/runelite/model/RuneLite",
                        "<init>",
                        "()V"
                )
                .invokeStatic(
                        "com/tonic/Main",
                        "setRunelite",
                        "(Lcom/tonic/runelite/model/RuneLite;)V"
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
                .invokeStatic("com/tonic/Main",
                        "getRunelite",
                        "()Lcom/tonic/runelite/model/RuneLite;")
                .invokeVirtual("com/tonic/runelite/Install",
                        "start",
                        "(Lcom/tonic/runelite/model/RuneLite;)V")
                .build();


        main.instructions.insert(insertionPoint, code);
        System.out.println("Injected side load call into RuneLite main method");
    }
}
