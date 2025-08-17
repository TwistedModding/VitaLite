package com.tonic.injector.rlpipeline;

import com.tonic.Main;
import com.tonic.util.BytecodeBuilder;
import com.tonic.util.LdcRewriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class PatchSplashScreenTransformer
{
    public static void patch(ClassNode classNode) {
        if(Main.optionsParser.isIncognito())
            return;

        if (!classNode.name.equals("net/runelite/client/ui/SplashScreen"))
            return;

        //get no arg constructor MethodNode
        MethodNode constructor = classNode.methods.stream()
                .filter(m -> m.name.equals("<init>") && m.desc.equals("()V"))
                .findFirst()
                .orElse(null);

        if (constructor == null) {
            System.err.println("Could not find constructor in SplashScreen class");
            return;
        }

        //add call to static method com.tonic.runelite.patchSplashScreen(this) at end of method
        AbstractInsnNode target = null;
        for(AbstractInsnNode insn : constructor.instructions) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                target = insn;
                break;
            }
        }

        InsnList code = BytecodeBuilder.create()
                .pushThis()
                .invokeStatic(
                        "com/tonic/runelite/ClientUIUpdater",
                        "patchSplashScreen",
                        "(Ljavax/swing/JFrame;)V"
                ).build();
        constructor.instructions.insertBefore(
                target,
                code
        );

        for(AbstractInsnNode insn : constructor.instructions) {
            if (insn.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)insn).name.equals("BRAND_ORANGE")) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                fin.name = "GRAND_EXCHANGE_LIMIT";
            }
        }

        int strCount = LdcRewriter.rewriteString(constructor, "runelite_splash.png", "icon_splash.png");
        int clsCount = LdcRewriter.rewriteClassRef(constructor, "net/runelite/client/ui/SplashScreen", "com/tonic/Main");

        System.out.println("Strings Replaced: " + strCount);
        System.out.println("Class References Replaced: " + clsCount);
    }
}
