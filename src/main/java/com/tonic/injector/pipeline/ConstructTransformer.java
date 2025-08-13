package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.injector.Injector;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.Construct;
import com.tonic.injector.annotations.Mixin;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;

public class ConstructTransformer
{
    /**
     * Patches a mixin method annotated with @Construct to create an instance of the target class
     * @param mixin the mixin class node
     * @param method the method node annotated with @Construct
     */
    public static void patch(ClassNode mixin, MethodNode method) {
        String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
        String name = AnnotationUtil.getAnnotation(method, Construct.class, "value");
        JClass constructTargetDTO = MappingProvider.getClass(name);
        JClass injectionTargetDTO = MappingProvider.getClass(gamepackName);

        if (constructTargetDTO == null || injectionTargetDTO == null) {
            throw new RuntimeException("Construct target or injection target not found for " + name);
        }

        ClassNode constructTarget = Injector.gamepack.get(constructTargetDTO.getObfuscatedName());
        ClassNode injectionTarget = Injector.gamepack.get(injectionTargetDTO.getObfuscatedName());

        if (constructTarget == null || injectionTarget == null) {
            throw new RuntimeException("Construct target or injection target class not found for " + name);
        }

        Type[] paramTypes = Type.getArgumentTypes(method.desc);
        MethodNode targetConstructor = findMatchingConstructor(constructTarget, paramTypes);
        if (targetConstructor == null) {
            throw new RuntimeException("No matching constructor found in " + constructTarget.name +
                    " for parameters " + Arrays.toString(paramTypes));
        }

        InsnList instructions = new InsnList();
        instructions.add(new TypeInsnNode(Opcodes.NEW, constructTarget.name));
        instructions.add(new InsnNode(Opcodes.DUP));

        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int localVarIndex = isStatic ? 0 : 1;

        Type[] constructorParamTypes = Type.getArgumentTypes(targetConstructor.desc);

        for (int i = 0; i < paramTypes.length; i++) {
            Type paramType = paramTypes[i];
            instructions.add(new VarInsnNode(paramType.getOpcode(Opcodes.ILOAD), localVarIndex));
            if (i < constructorParamTypes.length) {
                Type expectedType = constructorParamTypes[i];
                if (!paramType.equals(expectedType) && needsCast(paramType, expectedType)) {
                    instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, expectedType.getInternalName()));
                }
            }

            localVarIndex += paramType.getSize();
        }

        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                constructTarget.name,
                "<init>",
                targetConstructor.desc,
                false
        ));

        instructions.add(new InsnNode(Opcodes.ARETURN));
        Type returnType = Type.getReturnType(method.desc);

        String returnDesc = Type.getMethodDescriptor(
                returnType,  // Use the original return type (TClientPacket)
                paramTypes
        );

        MethodNode factoryMethod = new MethodNode(
                method.access & ~Opcodes.ACC_ABSTRACT,
                method.name,
                returnDesc,
                null,
                method.exceptions != null ?
                        method.exceptions.toArray(new String[0]) : null
        );

        factoryMethod.instructions = instructions;
        factoryMethod.access &= ~Opcodes.ACC_ABSTRACT;

        injectionTarget.methods.add(factoryMethod);
    }

    private static MethodNode findMatchingConstructor(ClassNode classNode, Type[] paramTypes) {
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) {
                continue;
            }

            Type[] constructorParams = Type.getArgumentTypes(method.desc);

            if (isCompatibleSignature(paramTypes, constructorParams)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isCompatibleSignature(Type[] providedTypes, Type[] expectedTypes) {
        if (providedTypes.length != expectedTypes.length) {
            return false;
        }

        for (int i = 0; i < providedTypes.length; i++) {
            Type provided = providedTypes[i];
            Type expected = expectedTypes[i];

            if (!isAssignable(provided, expected)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAssignable(Type from, Type to) {
        if (from.equals(to)) {
            return true;
        }

        if (from.getSort() < Type.ARRAY && to.getSort() < Type.ARRAY) {
            return false;
        }

        if (from.getSort() == Type.OBJECT && to.getSort() == Type.OBJECT) {
            return true;
        }

        if (from.getSort() == Type.ARRAY && to.getSort() == Type.ARRAY) {
            return isAssignable(from.getElementType(), to.getElementType());
        }

        return false;
    }

    private static boolean needsCast(Type from, Type to) {
        // Only need cast for object/array types that are different
        return !from.equals(to) &&
                (from.getSort() == Type.OBJECT || from.getSort() == Type.ARRAY) &&
                (to.getSort() == Type.OBJECT || to.getSort() == Type.ARRAY);
    }
}
