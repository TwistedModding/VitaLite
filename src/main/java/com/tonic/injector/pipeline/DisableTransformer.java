package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.dto.JMethod;
import com.tonic.injector.Injector;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.Disable;
import com.tonic.injector.annotations.Mixin;
import com.tonic.model.ConditionType;
import com.tonic.injector.util.AnnotationUtil;
import com.tonic.injector.util.BytecodeBuilder;
import com.tonic.injector.util.InsnUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class DisableTransformer
{
    public static void patch(ClassNode mixin, MethodNode method)
    {
        if(!method.desc.endsWith(")Z"))
        {
            throw new RuntimeException("Method " + method.name + " in mixin " + mixin.name + " must be a boolean method.");
        }

        String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
        String name = AnnotationUtil.getAnnotation(method, Disable.class, "value");
        JClass jClass = MappingProvider.getClass(gamepackName);
        JMethod jMethod = MappingProvider.getMethod(jClass, name);

        if (jMethod == null)
        {
            throw new RuntimeException("Method " + name + " not found in class " + gamepackName);
        }

        ClassNode gamepack = Injector.gamepack.get(jMethod.getOwnerObfuscatedName());
        InjectTransformer.patch(gamepack, mixin, method);

        MethodNode toHook = gamepack.methods.stream()
                .filter(m -> m.name.equals(jMethod.getObfuscatedName()) && m.desc.equals(jMethod.getDescriptor()))
                .findFirst()
                .orElse(null);

        if (toHook == null) {
            System.err.println("Could not find method to hook: " + name);
            return;
        }

        Type hookMethodType = Type.getMethodType(method.desc);
        Type targetMethodType = Type.getMethodType(toHook.desc);
        Type[] hookParams = hookMethodType.getArgumentTypes();
        Type[] targetParams = targetMethodType.getArgumentTypes();

        InsnList call = new InsnList();
        if (hookParams.length == 0) {
            call.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    gamepack.name,
                    method.name,
                    method.desc,
                    false
            ));
        }
        else {
            if (hookParams.length > targetParams.length) {
                System.err.println("Hook method expects more parameters than target method has");
                return;
            }

            boolean isTargetStatic = (toHook.access & Opcodes.ACC_STATIC) != 0;
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
                        if (!targetParamType.equals(hookParamType) &&
                                hookParamType.getSort() == Type.OBJECT) {
                            call.add(new TypeInsnNode(Opcodes.CHECKCAST,
                                    hookParamType.getInternalName()));
                        }
                        break;
                }

                localVarIndex += targetParamType.getSize();
            }
            call.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    gamepack.name,
                    method.name,
                    method.desc,
                    false
            ));
        }

        InsnList instructions = BytecodeBuilder.create()
                .ifBlock(
                        ConditionType.NOT_EQUALS,
                        b -> b.appendInsnList(call).pushInt(0),
                        b -> {
                            InsnList returnInsnList = InsnUtil.generateDefaultReturn(toHook);
                            b.appendInsnList(returnInsnList);
                        }
                ).build();

        AbstractInsnNode injectionPoint = null;

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

        boolean isHookedStatic = (toHook.access & Opcodes.ACC_STATIC) != 0;
        if (injectionPoint != null) {
            toHook.instructions.insert(injectionPoint, instructions);
        } else {
            if(isHookedStatic)
                toHook.instructions.insert(instructions);
            else
                toHook.instructions.insert(toHook.instructions.getFirst(), instructions);
        }
    }
}
