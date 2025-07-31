package com.tonic.injector.rlpipeline;

import com.tonic.util.BytecodeBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class PatchDevToolsLauncherCheck
{
    public static void patch(ClassNode classNode) {
        if (!classNode.name.equals("net/runelite/client/RuneLite"))
            return;

        MethodNode main = classNode.methods.stream()
                .filter(method -> method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V"))
                .findFirst()
                .orElse(null);

        if (main == null) {
            System.out.println("Failed to find RuneLite main start");
            return;
        }

        int devSlot = -1;
        if (main.localVariables != null) {
            for (LocalVariableNode lv : main.localVariables) {
                if ("developerMode".equals(lv.name)) {
                    devSlot = lv.index;
                    break;
                }
            }
        }

        if (devSlot < 0) {
            System.out.println("Failed to find developerMode local variable in RuneLite.main()");
            return;
        }

        AbstractInsnNode insertionPoint = null;
        for (AbstractInsnNode insn : main.instructions) {
            if (insn.getOpcode() == Opcodes.ISTORE) {
                VarInsnNode vin = (VarInsnNode) insn;
                if(vin.var == devSlot)
                {
                    insertionPoint = vin;
                }
            }
        }
        if (insertionPoint == null) {
            System.out.println("Couldn’t locate RuneLite.main() call");
            return;
        }

        InsnList code = BytecodeBuilder.create()
                .pushInt(1)
                .storeLocal(devSlot, Opcodes.ISTORE)
                .build();

        main.instructions.insert(insertionPoint, code);
    }
}
