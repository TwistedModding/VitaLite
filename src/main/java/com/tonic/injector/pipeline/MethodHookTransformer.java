package com.tonic.injector.pipeline;

import com.tonic.injector.annotations.MethodHook;
import com.tonic.injector.util.AnnotationUtil;
import com.tonic.injector.util.TransformerUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Injects method calls at the beginning of target methods.
 */
public class MethodHookTransformer
{
    /**
     * Processes a {@link MethodHook} annotated method and injects it as a hook into the target method.
     * <p>
     * This method performs the complete hook injection process:
     * <ol>
     *   <li>Extracts the target method name from the {@link MethodHook} annotation</li>
     *   <li>Injects the hook method into the target class using {@link InjectTransformer}</li>
     *   <li>Locates the target method in the gamepack</li>
     *   <li>Generates appropriate bytecode to call the hook method</li>
     *   <li>Inserts the hook call at the method's entry point</li>
     * </ol>
     * <p>
     * Parameter handling:
     * <ul>
     *   <li>Hook methods with no parameters are called directly</li>
     *   <li>Hook methods with parameters receive values from the target method's parameters</li>
     *   <li>Parameter count validation ensures hook doesn't expect more parameters than available</li>
     *   <li>Type casting is applied for object parameters when types don't match exactly</li>
     * </ul>
     * <p>
     * Constructor handling:
     * <ul>
     *   <li>For constructors, the hook is injected after the super() constructor call</li>
     *   <li>This ensures the object is properly initialized before hook execution</li>
     * </ul>
     * 
     * @param mixin the mixin class node containing the hook method
     * @param method the method node annotated with {@link MethodHook} to be used as a hook
     * @throws RuntimeException if the target method cannot be found
     */
    public static void patch(ClassNode mixin, MethodNode method) {
        String name = AnnotationUtil.getAnnotation(method, MethodHook.class, "value");
        ClassNode gamepack = TransformerUtil.getMethodClass(mixin, name);
        InjectTransformer.patch(gamepack, mixin, method);

        MethodNode toHook = TransformerUtil.getTargetMethod(mixin, name);

        if (toHook == null) {
            System.err.println("Could not find method to hook: " + name);
            return;
        }

        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        Type hookMethodType = Type.getMethodType(method.desc);
        Type targetMethodType = Type.getMethodType(toHook.desc);
        Type[] hookParams = hookMethodType.getArgumentTypes();
        Type[] targetParams = targetMethodType.getArgumentTypes();

        InsnList call = new InsnList();
        if (hookParams.length == 0) {
            if(isStatic)
            {
                call.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        gamepack.name,
                        method.name,
                        method.desc,
                        false
                ));
            }
            else
            {
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        gamepack.name,
                        method.name,
                        method.desc,
                        false
                ));
            }
        }
        else {
            if (hookParams.length > targetParams.length) {
                System.err.println("Hook method expects more parameters than target method has");
                return;
            }

            if(!isStatic)
            {
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
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
                    default:
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

            if(isStatic)
            {
                call.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        gamepack.name,
                        method.name,
                        method.desc,
                        false
                ));
            }
            else
            {
                call.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        gamepack.name,
                        method.name,
                        method.desc,
                        false
                ));
            }
        }

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

        if (injectionPoint != null) {
            toHook.instructions.insert(injectionPoint, call);
        } else {
            toHook.instructions.insert(call);
        }
    }
}
