package com.tonic.injector.rlpipeline;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * ASM transformer that replaces all calls to scheduleWithFixedDelay(...) with null.
 * Direct ASM equivalent of Javassist's "{ $_ = null; }" replacement.
 */
public class ScheduleWithFixedDelayTransformer implements Opcodes {

    public static void patch(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            InsnList instructions = method.instructions;
            AbstractInsnNode[] insnArray = instructions.toArray();

            for (AbstractInsnNode insn : insnArray) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;

                    if (methodInsn.name.equals("submitPlugins")) {
                        System.out.println("Replacing submitPlugins in " + classNode.name + "." + method.name);

                        // Create replacement instructions
                        InsnList replacement = new InsnList();

                        // Parse method descriptor to get argument types
                        Type[] argTypes = Type.getArgumentTypes(methodInsn.desc);
                        Type returnType = Type.getReturnType(methodInsn.desc);

                        // Pop arguments in reverse order (last argument first)
                        for (int i = argTypes.length - 1; i >= 0; i--) {
                            Type argType = argTypes[i];
                            if (argType.getSize() == 2) { // long or double
                                replacement.add(new InsnNode(Opcodes.POP2));
                            } else {
                                replacement.add(new InsnNode(Opcodes.POP));
                            }
                        }

                        // Pop the object reference (for non-static calls)
                        if (methodInsn.getOpcode() != Opcodes.INVOKESTATIC) {
                            replacement.add(new InsnNode(Opcodes.POP));
                        }

                        // Push return value if method returns something
                        if (returnType.getSort() != Type.VOID) {
                            if (returnType.getSort() == Type.LONG) {
                                replacement.add(new InsnNode(Opcodes.LCONST_0));
                            } else if (returnType.getSort() == Type.DOUBLE) {
                                replacement.add(new InsnNode(Opcodes.DCONST_0));
                            } else if (returnType.getSort() == Type.FLOAT) {
                                replacement.add(new InsnNode(Opcodes.FCONST_0));
                            } else if (returnType.getSort() >= Type.BOOLEAN && returnType.getSort() <= Type.INT) {
                                replacement.add(new InsnNode(Opcodes.ICONST_0));
                            } else {
                                // Object type - push null
                                replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                            }
                        }

                        // Replace the method call
                        instructions.insert(insn, replacement);
                        instructions.remove(insn);
                    }
                }
            }
        }
    }

    // Alternative approach: Even simpler - just make submitPlugins return immediately
    public static void patch2(ClassNode classNode){

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