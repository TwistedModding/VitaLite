package com.tonic.injector.util;

import com.tonic.injector.types.CopyMethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class MethodUtil
{

    /**
     * Copy a method with a new name
     */
    public static MethodNode copyMethod(MethodNode original, String newName, ClassNode mixinClass, ClassNode gamepackClass) {
        MethodNode copy = new MethodNode(
                original.access,
                newName,
                original.desc,
                original.signature,
                original.exceptions.toArray(new String[0])
        );

        // Copy instructions with transformation
        original.accept(new CopyMethodVisitor(Opcodes.ASM9, copy, mixinClass, gamepackClass));

        // Update the name
        copy.name = newName;

        return copy;
    }

    public static InsnList generateContextAwareInvoke(ClassNode clazz, MethodNode target, MethodNode hook, boolean discardReturn)
    {
        InsnList code = new InsnList();
        Type injectedMethodType = Type.getMethodType(hook.desc);
        Type[] hookParams = injectedMethodType.getArgumentTypes();
        boolean isInjectedStatic = (hook.access & Opcodes.ACC_STATIC) != 0;
        boolean isTargetStatic = (target.access & Opcodes.ACC_STATIC) != 0;

        if (!isInjectedStatic) {
            if (isTargetStatic) {
                throw new RuntimeException("Cannot call non-static injected method from static target method");
            }
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }

        if(hookParams.length > 0)
        {
            Type[] targetParams = Type.getArgumentTypes(target.desc);
            if (hookParams.length > targetParams.length) {
                System.err.println("Hook method expects more parameters than target method has");
                throw new RuntimeException("Hook method expects more parameters than target method has");
            }

            int localVarIndex = isTargetStatic ? 0 : 1;

            for (int i = 0; i < hookParams.length; i++) {
                Type targetParamType = targetParams[i];
                Type hookParamType = hookParams[i];

                switch (targetParamType.getSort()) {
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                        code.add(new VarInsnNode(Opcodes.ILOAD, localVarIndex));
                        break;
                    case Type.FLOAT:
                        code.add(new VarInsnNode(Opcodes.FLOAD, localVarIndex));
                        break;
                    case Type.LONG:
                        code.add(new VarInsnNode(Opcodes.LLOAD, localVarIndex));
                        break;
                    case Type.DOUBLE:
                        code.add(new VarInsnNode(Opcodes.DLOAD, localVarIndex));
                        break;
                    default:
                        code.add(new VarInsnNode(Opcodes.ALOAD, localVarIndex));
                        if (!targetParamType.equals(hookParamType) &&
                                hookParamType.getSort() == Type.OBJECT) {
                            code.add(new TypeInsnNode(Opcodes.CHECKCAST,
                                    hookParamType.getInternalName()));
                        }
                        break;
                }

                localVarIndex += targetParamType.getSize();
            }
        }

        int invokeOpcode = isInjectedStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
        code.add(new MethodInsnNode(
                invokeOpcode,
                clazz.name,
                hook.name,
                hook.desc,
                false
        ));

        if(discardReturn)
        {
            Type returnType = injectedMethodType.getReturnType();
            if (returnType.getSort() != Type.VOID) {
                if (returnType.getSize() == 2) {
                    code.add(new InsnNode(Opcodes.POP2));
                } else {
                    code.add(new InsnNode(Opcodes.POP));
                }
            }
        }

        return code;
    }
}
