package com.tonic.injector.rlpipeline;

import com.tonic.injector.util.BytecodeBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class RuneLiteObjectStoreTransformer
{
    public static void patch(ClassNode classNode) {
        if (!classNode.name.equals("net/runelite/client/RuneLite"))
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
            if (insn.getOpcode() == Opcodes.CHECKCAST) {
                TypeInsnNode m = (TypeInsnNode) insn;
                if (m.desc.equals("net/runelite/client/RuneLite")) {
                    insertionPoint = insn;
                    break;
                }
            }
        }
        if (insertionPoint == null) {
            System.out.println("Couldn’t locate RuneLite::main call point");
            System.exit(0);
            return;
        }

        FieldNode rlInstanceField = new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "rlInstance",
                "Lnet/runelite/client/RuneLite;",
                null,
                null
        );
        classNode.fields.add(rlInstanceField);

        InsnList toInject = BytecodeBuilder.create()
                .dup()
                .putStaticField("net/runelite/client/RuneLite", "rlInstance", "Lnet/runelite/client/RuneLite;")
                .build();

        main.instructions.insert(insertionPoint, toInject);
    }
}
