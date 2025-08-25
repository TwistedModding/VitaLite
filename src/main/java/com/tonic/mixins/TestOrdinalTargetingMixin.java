package com.tonic.mixins;

import com.tonic.injector.annotations.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

@Mixin("Client")
public class TestOrdinalTargetingMixin {
    
    /**
     * Example: Insert after the LAST CHECKCAST using ordinal = -1
     * This targets the final occurrence of the pattern in the method
     */
    @Insert(
            method = "main",
            at = @At(
                    value = AtTarget.CHECKCAST,
                    owner = "net/runelite/client/RuneLite"
            ),
            ordinal = -1,
            raw = true
    )
    public static void afterLastCheckcastToRuneLite(MethodNode method, AbstractInsnNode insn) {
        System.out.println("Found LAST CHECKCAST to net/runelite/client/RuneLite at instruction: " + insn);
        if (insn instanceof org.objectweb.asm.tree.TypeInsnNode) {
            org.objectweb.asm.tree.TypeInsnNode typeInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
            System.out.println("CHECKCAST target type: " + typeInsn.desc);
        }
    }
    
    /**
     * Example: Insert after the FIRST CHECKCAST (ordinal = 0, which is default)
     */
    @Insert(
            method = "processData",
            at = @At(
                    value = AtTarget.CHECKCAST,
                    owner = "Player"
            ),
            ordinal = 0
    )
    public static void afterFirstCheckcastToPlayer() {
        System.out.println("Hooked after FIRST CHECKCAST to Player");
    }
    
    /**
     * Example: Insert after the SECOND CHECKCAST (ordinal = 1)
     */
    @Insert(
            method = "processData", 
            at = @At(
                    value = AtTarget.CHECKCAST,
                    owner = "Player"
            ),
            ordinal = 1
    )
    public static void afterSecondCheckcastToPlayer() {
        System.out.println("Hooked after SECOND CHECKCAST to Player");
    }
    
    /**
     * Example: Insert after the LAST local variable load (ordinal = -1)
     */
    @Insert(
            method = "handleVariables",
            at = @At(value = AtTarget.LOAD, local = 5),
            ordinal = -1
    )
    public static void afterLastLoadLocal5() {
        System.out.println("Hooked after LAST load of local variable #5");
    }
    
    /**
     * Example: Insert after the LAST method invocation (ordinal = -1)
     */
    @Insert(
            method = "chainedCalls",
            at = @At(
                    value = AtTarget.INVOKE,
                    target = "toString"
            ),
            ordinal = -1
    )
    public static void afterLastToStringCall() {
        System.out.println("Hooked after LAST toString() call");
    }
}