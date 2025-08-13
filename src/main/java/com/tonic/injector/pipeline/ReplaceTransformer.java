package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.dto.JMethod;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.MethodHook;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Replace;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class ReplaceTransformer
{
    /**
     * This transformer replaces a method in the gamepack with a static hook method.
     * The hook method is injected into the gamepack and then used to override the target method.
     * The target method is identified by its name and descriptor, which are obtained from the mixin's
     * @MethodHook annotation and the @Replace annotation on the method.
     *
     * @param gamepack The ClassNode of the gamepack being modified.
     * @param mixin    The ClassNode of the mixin containing the hook method.
     * @param method   The MethodNode of the hook method to be injected.
     */
    public static void patch(ClassNode gamepack, ClassNode mixin, MethodNode method) {
        // First, inject the hook method into the gamepack (static copy)
        InjectTransformer.patch(gamepack, mixin, method);

        // Read the @MethodHook annotation to find which target to override
        String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
        String name = AnnotationUtil.getAnnotation(method, Replace.class, "value");
        JClass jClass = MappingProvider.getClass(gamepackName);
        JMethod jMethod = MappingProvider.getMethod(jClass, name);

        String targetName = jMethod.getObfuscatedName();
        String targetDesc = jMethod.getDescriptor();

        // Locate the target method in the gamepack
        MethodNode toReplace = gamepack.methods.stream()
                .filter(m -> m.name.equals(targetName) && m.desc.equals(targetDesc))
                .findFirst()
                .orElse(null);

        if (toReplace == null) {
            System.err.println("Could not find method to override: " + targetName + targetDesc);
            return;
        }

        // Parse hook and target descriptors
        Type hookType   = Type.getMethodType(method.desc);
        Type targetType = Type.getMethodType(toReplace.desc);
        Type[] hookParams   = hookType.getArgumentTypes();
        Type[] targetParams = targetType.getArgumentTypes();
        Type   returnType   = hookType.getReturnType();

        // Build instructions: load args, invoke hook, return result
        InsnList override = new InsnList();
        boolean isStatic = (toReplace.access & Opcodes.ACC_STATIC) != 0;
        int localIdx = isStatic ? 0 : 1;

        // Load each method parameter onto the stack
        for (int i = 0; i < hookParams.length; i++) {
            Type t = targetParams[i];
            switch (t.getSort()) {
                case Type.BOOLEAN: case Type.CHAR: case Type.BYTE:
                case Type.SHORT:   case Type.INT:
                    override.add(new VarInsnNode(Opcodes.ILOAD,  localIdx));
                    break;
                case Type.FLOAT:
                    override.add(new VarInsnNode(Opcodes.FLOAD,  localIdx));
                    break;
                case Type.LONG:
                    override.add(new VarInsnNode(Opcodes.LLOAD,  localIdx));
                    break;
                case Type.DOUBLE:
                    override.add(new VarInsnNode(Opcodes.DLOAD,  localIdx));
                    break;
                default:
                    override.add(new VarInsnNode(Opcodes.ALOAD,  localIdx));
            }
            localIdx += t.getSize();
        }

        // Invoke the injected hook method (static)
        override.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                gamepack.name,
                method.name,
                method.desc,
                false
        ));

        // Append the appropriate return instruction
        int retOp;
        switch (returnType.getSort()) {
            case Type.VOID:
                retOp = Opcodes.RETURN;
                break;
            case Type.BOOLEAN: case Type.CHAR: case Type.BYTE:
            case Type.SHORT:   case Type.INT:
                retOp = Opcodes.IRETURN;
                break;
            case Type.FLOAT:
                retOp = Opcodes.FRETURN;
                break;
            case Type.LONG:
                retOp = Opcodes.LRETURN;
                break;
            case Type.DOUBLE:
                retOp = Opcodes.DRETURN;
                break;
            default:
                retOp = Opcodes.ARETURN;
        }
        override.add(new InsnNode(retOp));

        // Insert at the very start of the target method
        toReplace.instructions.insert(override);

        System.out.println("::: Overrode Method: " + targetName + targetDesc
                + " -> returns hook " + method.name + method.desc);
    }
}
