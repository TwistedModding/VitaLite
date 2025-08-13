package com.tonic.injector.rlpipeline;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Transforms the submitPlugins method to be a no-op.
 */
public class ScheduleWithFixedDelayTransformer implements Opcodes {

    public static void patch(ClassNode classNode){

        for (MethodNode method : classNode.methods) {
            if (method.name.equals("submitPlugins")) {
                System.out.println("Making submitPlugins a no-op in " + classNode.name);

                // Clear the method body
                method.instructions.clear();

                // Add return instruction based on return type
                Type returnType = Type.getReturnType(method.desc);
                if (returnType.getSort() == Type.VOID) {
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                } else if (returnType.getSort() == Type.LONG) {
                    method.instructions.add(new InsnNode(Opcodes.LCONST_0));
                    method.instructions.add(new InsnNode(Opcodes.LRETURN));
                } else if (returnType.getSort() == Type.DOUBLE) {
                    method.instructions.add(new InsnNode(Opcodes.DCONST_0));
                    method.instructions.add(new InsnNode(Opcodes.DRETURN));
                } else if (returnType.getSort() == Type.FLOAT) {
                    method.instructions.add(new InsnNode(Opcodes.FCONST_0));
                    method.instructions.add(new InsnNode(Opcodes.FRETURN));
                } else if (returnType.getSort() >= Type.BOOLEAN && returnType.getSort() <= Type.INT) {
                    method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                    method.instructions.add(new InsnNode(Opcodes.IRETURN));
                } else {
                    // Object type - return null
                    method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    method.instructions.add(new InsnNode(Opcodes.ARETURN));
                }

                // Reset max stack and locals (will be recalculated by ClassWriter)
                method.maxStack = 1;
                method.maxLocals = Type.getArgumentTypes(method.desc).length + (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            }
        }
    }
}