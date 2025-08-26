package com.tonic.injector.pipeline;

import com.tonic.injector.annotations.Construct;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.util.AnnotationUtil;
import com.tonic.injector.util.TransformerUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;

/**
 * Transformer that processes @Construct annotations to generate factory methods for gamepack class construction.
 * Converts annotated method signatures into appropriate constructor calls with parameter mapping and type casting.
 */
public class ConstructTransformer
{
    /**
     * Patches a mixin method annotated with @Construct to create an instance of the target class.
     * Generates a factory method that creates and returns a new instance using the appropriate constructor.
     *
     * @param mixin the mixin class node containing the annotated method
     * @param method the method node annotated with @Construct
     * @throws RuntimeException if target classes or matching constructors cannot be found
     */
    public static void patch(ClassNode mixin, MethodNode method) {
        String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
        String name = AnnotationUtil.getAnnotation(method, Construct.class, "value");

        if (gamepackName == null || name == null) {
            throw new RuntimeException("Construct target or injection target not found for " + name);
        }

        ClassNode constructTarget = TransformerUtil.getBaseClass(name);
        ClassNode injectionTarget = TransformerUtil.getBaseClass(gamepackName);

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
                returnType,
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

    /**
     * Finds a constructor in the target class that matches the provided parameter types.
     *
     * @param classNode the class to search for constructors
     * @param paramTypes the parameter types to match
     * @return the matching constructor method node, or null if no match found
     */
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

    /**
     * Checks if the provided parameter types are compatible with the expected parameter types.
     *
     * @param providedTypes the types being provided
     * @param expectedTypes the types expected by the constructor
     * @return true if the signatures are compatible
     */
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

    /**
     * Determines if a type can be assigned to another type.
     *
     * @param from the source type
     * @param to the target type
     * @return true if the assignment is valid
     */
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

    /**
     * Determines if a cast instruction is needed when converting between types.
     *
     * @param from the source type
     * @param to the target type
     * @return true if a cast instruction should be generated
     */
    private static boolean needsCast(Type from, Type to) {
        return !from.equals(to) &&
                (from.getSort() == Type.OBJECT || from.getSort() == Type.ARRAY) &&
                (to.getSort() == Type.OBJECT || to.getSort() == Type.ARRAY);
    }
}
