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

        // Parse parameter types from constructorMethod
        Type[] paramTypes = Type.getArgumentTypes(method.desc);

        // Find matching constructor in constructTarget
        MethodNode targetConstructor = findMatchingConstructor(constructTarget, paramTypes);

        if (targetConstructor == null) {
            throw new RuntimeException("No matching constructor found in " + constructTarget.name +
                    " for parameters " + Arrays.toString(paramTypes));
        }

        // Create the method body
        InsnList instructions = new InsnList();

        // NEW instruction - create new instance
        instructions.add(new TypeInsnNode(Opcodes.NEW, constructTarget.name));
        // DUP the reference for constructor call
        instructions.add(new InsnNode(Opcodes.DUP));

        // Load parameters
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int localVarIndex = isStatic ? 0 : 1;

        Type[] constructorParamTypes = Type.getArgumentTypes(targetConstructor.desc);

        for (int i = 0; i < paramTypes.length; i++) {
            Type paramType = paramTypes[i];
            // Load the parameter
            instructions.add(new VarInsnNode(paramType.getOpcode(Opcodes.ILOAD), localVarIndex));

            // Cast if necessary - the constructor might expect a more specific type
            if (i < constructorParamTypes.length) {
                Type expectedType = constructorParamTypes[i];
                if (!paramType.equals(expectedType) && needsCast(paramType, expectedType)) {
                    instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, expectedType.getInternalName()));
                }
            }

            localVarIndex += paramType.getSize();
        }

        // INVOKESPECIAL to call constructor
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                constructTarget.name,
                "<init>",
                targetConstructor.desc,
                false
        ));

        // ARETURN - return the new instance
        instructions.add(new InsnNode(Opcodes.ARETURN));

        // Create the factory method with return type of constructTarget
        String returnDesc = Type.getMethodDescriptor(
                Type.getObjectType(constructTarget.name),
                paramTypes
        );

        MethodNode factoryMethod = new MethodNode(
                method.access & ~Opcodes.ACC_ABSTRACT, // Remove abstract flag if present
                method.name,
                returnDesc,
                null,
                method.exceptions != null ?
                        method.exceptions.toArray(new String[0]) : null
        );

        factoryMethod.instructions = instructions;

        // Add the method to injectionTarget
        injectionTarget.methods.add(factoryMethod);
    }

    private static MethodNode findMatchingConstructor(ClassNode classNode, Type[] paramTypes) {
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) {
                continue;
            }

            Type[] constructorParams = Type.getArgumentTypes(method.desc);

            if (isCompatibleSignature(paramTypes, constructorParams, classNode)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isCompatibleSignature(Type[] providedTypes, Type[] expectedTypes, ClassNode context) {
        if (providedTypes.length != expectedTypes.length) {
            return false;
        }

        for (int i = 0; i < providedTypes.length; i++) {
            Type provided = providedTypes[i];
            Type expected = expectedTypes[i];

            // Check if types are compatible
            if (!isAssignable(provided, expected, context)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAssignable(Type from, Type to, ClassNode context) {
        // Same type - always compatible
        if (from.equals(to)) {
            return true;
        }

        // Primitive types must match exactly
        if (from.getSort() < Type.ARRAY && to.getSort() < Type.ARRAY) {
            return false;
        }

        // For object types, check inheritance
        if (from.getSort() == Type.OBJECT && to.getSort() == Type.OBJECT) {
            // This is a simplified check - in a real implementation you'd need to:
            // 1. Load the class hierarchy from the gamepack
            // 2. Check if 'to' is assignable from 'from'
            // For now, we'll assume the user knows what they're doing
            // and allow any object-to-object assignment
            return true;
        }

        // Arrays need special handling
        if (from.getSort() == Type.ARRAY && to.getSort() == Type.ARRAY) {
            return isAssignable(from.getElementType(), to.getElementType(), context);
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
