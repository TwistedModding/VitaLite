package com.tonic.injector.pipeline;

import com.tonic.injector.annotations.MethodHook;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class MethodHookTransformer
{
    public static void patch(ClassNode gamepack, ClassNode mixin, MethodNode method) {
        InjectTransformer.patch(gamepack, mixin, method);
        String methodName = AnnotationUtil.getAnnotation(method, MethodHook.class, "name");
        String methodDesc = AnnotationUtil.getAnnotation(method, MethodHook.class, "desc");
        MethodNode toHook = gamepack.methods.stream()
                .filter(m -> m.name.equals(methodName) && m.desc.equals(methodDesc))
                .findFirst()
                .orElse(null);

        if (toHook == null) {
            System.err.println("Could not find method to hook: " + methodName);
            return;
        }

        // Parse method descriptors
        Type hookMethodType = Type.getMethodType(method.desc);
        Type targetMethodType = Type.getMethodType(toHook.desc);
        Type[] hookParams = hookMethodType.getArgumentTypes();
        Type[] targetParams = targetMethodType.getArgumentTypes();

        // Build the call
        InsnList call = new InsnList();

        // Case 1: Hook method has no parameters
        if (hookParams.length == 0) {
            // Simple static call with no arguments
            call.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    gamepack.name,
                    method.name,
                    method.desc,
                    false
            ));
        }
        // Case 2: Hook method has parameters
        else {
            // Check if we have enough parameters in target method
            if (hookParams.length > targetParams.length) {
                System.err.println("Hook method expects more parameters than target method has");
                return;
            }

            // Load parameters from target method
            boolean isTargetStatic = (toHook.access & Opcodes.ACC_STATIC) != 0;
            int localVarIndex = isTargetStatic ? 0 : 1; // Skip 'this' if not static

            for (int i = 0; i < hookParams.length; i++) {
                Type targetParamType = targetParams[i];
                Type hookParamType = hookParams[i];

                // Load the parameter
                switch (targetParamType.getSort()) {
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                        call.add(new VarInsnNode(Opcodes.ILOAD, localVarIndex));
                        break;
                    case Type.FLOAT:
                        call.add(new VarInsnNode(Opcodes.FLOAD, localVarIndex));
                        break;
                    case Type.LONG:
                        call.add(new VarInsnNode(Opcodes.LLOAD, localVarIndex));
                        break;
                    case Type.DOUBLE:
                        call.add(new VarInsnNode(Opcodes.DLOAD, localVarIndex));
                        break;
                    default: // Object/Array
                        call.add(new VarInsnNode(Opcodes.ALOAD, localVarIndex));
                        // Add cast if types differ
                        if (!targetParamType.equals(hookParamType) &&
                                hookParamType.getSort() == Type.OBJECT) {
                            call.add(new TypeInsnNode(Opcodes.CHECKCAST,
                                    hookParamType.getInternalName()));
                        }
                        break;
                }

                localVarIndex += targetParamType.getSize(); // Long and Double take 2 slots
            }

            // Make the static call
            call.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    gamepack.name,
                    method.name,
                    method.desc,
                    false
            ));
        }

        // Find injection point
        AbstractInsnNode injectionPoint = null;

        // For constructors, inject after super() call
        if (toHook.name.equals("<init>")) {
            for (AbstractInsnNode insn : toHook.instructions) {
                if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.name.equals("<init>")) {
                        injectionPoint = insn;
                        break;
                    }
                }
            }
        }

        // Inject the call
        if (injectionPoint != null) {
            toHook.instructions.insert(injectionPoint, call);
        } else {
            toHook.instructions.insert(call);
        }
    }
}
