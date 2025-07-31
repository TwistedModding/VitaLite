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
                .filter(method -> method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V"))
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
                if (m.owner.equals("net/runelite/client/RuneLite")
                        && m.name.equals("start")
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
                //new RuneLite()
                .newInstance("com/tonic/runelite/model/RuneLite")
                .dup()
                .invokeSpecial(
                        "com/tonic/runelite/model/RuneLite",
                        "<init>",
                        "()V"
                )

                // Main.setRUNELITE(<new-instance>)
                .invokeStatic(
                        "com/tonic/Main",
                        "setRunelite",
                        "(Lcom/tonic/runelite/model/RuneLite;)V"
                )

                // Install.start(Main.getRunelite())
                .invokeStatic("com/tonic/Main",
                        "getRunelite",
                        "()Lcom/tonic/runelite/model/RuneLite;")
                .invokeStatic("com/tonic/runelite/Install",
                        "start",
                        "(Lcom/tonic/runelite/model/RuneLite;)V")
                .build();


        main.instructions.insert(insertionPoint, code);
        System.out.println("Injected side load call into RuneLite main method");
    }
}
