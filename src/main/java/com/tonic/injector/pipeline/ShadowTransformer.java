package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.dto.JField;
import com.tonic.dto.JMethod;
import com.tonic.injector.Injector;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.MethodHook;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;

public class ShadowTransformer
{
    /**
     * Replaces the given method's body with a call to the target method in the gamepack,
     * effectively making it a proxy to that method. It handles parameter passing, return types,
     * and any necessary type casting.
     *
     * @param mixin  The class node representing the mixin containing the shadow method.
     * @param method The method node representing the shadow method to be patched.
     */
    public static void patch(ClassNode mixin, MethodNode method) {
        String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
        String name = AnnotationUtil.getAnnotation(method, Shadow.class, "value");
        JClass jClass = MappingProvider.getClass(gamepackName);
        JMethod jMethod = MappingProvider.getMethod(jClass, name);

        if( jMethod == null ) {
            System.out.println("No mapping found for " + gamepackName + "." + name + " // " + mixin.name + "." + method.name + method.desc);
            System.exit(0);
            return;
        }

        ClassNode gamepack = Injector.gamepack.get(jMethod.getOwnerObfuscatedName());
        ClassNode injectionSite = Injector.gamepack.get(jClass.getObfuscatedName());

        MethodNode toShadow = gamepack.methods.stream()
                .filter(m -> m.name.equals(jMethod.getObfuscatedName()) && m.desc.equals(jMethod.getDescriptor()))
                .findFirst()
                .orElse(null);

        Number multiplier = jMethod.getGarbageValue();
        InsnList instructions = new InsnList();

        Type shadowReturnType = Type.getReturnType(method.desc);
        Type[] shadowParams = Type.getArgumentTypes(method.desc);
        Type[] toShadowParams = Type.getArgumentTypes(toShadow.desc);

        boolean isTargetStatic = (toShadow.access & Opcodes.ACC_STATIC) != 0;
        boolean isShadowStatic = (method.access & Opcodes.ACC_STATIC) != 0;

        int localVarIndex = isShadowStatic ? 0 : 1; // Skip 'this' if shadow method is not static
        if (!isTargetStatic) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }

        for (int i = 0; i < shadowParams.length; i++) {
            Type paramType = shadowParams[i];
            instructions.add(new VarInsnNode(paramType.getOpcode(Opcodes.ILOAD), localVarIndex));

            if (i < toShadowParams.length) {
                Type expectedType = toShadowParams[i];
                if (!paramType.equals(expectedType)) {
                    if (expectedType.getSort() == Type.OBJECT || expectedType.getSort() == Type.ARRAY) {
                        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, expectedType.getInternalName()));
                    }
                }
            }

            localVarIndex += paramType.getSize();
        }

        if (multiplier != null && toShadowParams.length > shadowParams.length) {
            Type garbageType = toShadowParams[toShadowParams.length - 1];

            if (multiplier instanceof Integer) {
                instructions.add(new LdcInsnNode(multiplier.intValue()));
                if (garbageType.getSort() == Type.BYTE) {
                    instructions.add(new InsnNode(Opcodes.I2B));
                } else if (garbageType.getSort() == Type.SHORT) {
                    instructions.add(new InsnNode(Opcodes.I2S));
                } else if (garbageType.getSort() == Type.CHAR) {
                    instructions.add(new InsnNode(Opcodes.I2C));
                }
            } else if (multiplier instanceof Long) {
                instructions.add(new LdcInsnNode(multiplier.longValue()));
            } else if (multiplier instanceof Float) {
                instructions.add(new LdcInsnNode(multiplier.floatValue()));
            } else if (multiplier instanceof Double) {
                instructions.add(new LdcInsnNode(multiplier.doubleValue()));
            } else {
                instructions.add(new LdcInsnNode(multiplier.intValue()));
            }
        }

        int invokeOpcode = isTargetStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
        instructions.add(new MethodInsnNode(
                invokeOpcode,
                gamepack.name,
                toShadow.name,
                toShadow.desc,
                false
        ));

        Type toShadowReturnType = Type.getReturnType(toShadow.desc);
        if (shadowReturnType.getSort() == Type.VOID) {
            instructions.add(new InsnNode(Opcodes.RETURN));
        } else {
            if (!toShadowReturnType.equals(shadowReturnType)) {
                if (shadowReturnType.getSort() == Type.OBJECT || shadowReturnType.getSort() == Type.ARRAY) {
                    instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, shadowReturnType.getInternalName()));
                }
            }
            instructions.add(new InsnNode(shadowReturnType.getOpcode(Opcodes.IRETURN)));
        }

        method.access &= ~Opcodes.ACC_ABSTRACT;
        method.instructions = instructions;

        MethodNode injectedMethod = new MethodNode(
                method.access,
                method.name,
                method.desc,
                method.signature,
                method.exceptions != null ? method.exceptions.toArray(new String[0]) : null
        );
        injectedMethod.instructions = instructions;

        injectionSite.methods.add(injectedMethod);
    }

    /**
     * Transforms a shadow field in a mixin class to point to the corresponding field in the gamepack.
     * @param mixin the mixin class node containing the shadow field
     * @param field the field node representing the shadow field to be transformed
     */
    public static void patch(ClassNode mixin, FieldNode field) {
        try
        {
            String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
            String name = AnnotationUtil.getAnnotation(field, Shadow.class, "value");
            JClass jClass = MappingProvider.getClass(gamepackName);
            JField jField = MappingProvider.getField(jClass, name);

            String oldOwner = mixin.name;
            String oldName = field.name;
            String newOwner = jField.getOwnerObfuscatedName();
            String newName = jField.getObfuscatedName();

            Type oldType = Type.getType(field.desc);
            Type newType = Type.getType(jField.getDescriptor());

            for(MethodNode mn : mixin.methods)
            {
                transformProxyField(jField, mn, oldType, newType, oldName, oldOwner, newName, newOwner);
            }
        }
        catch (Exception e)
        {
            System.out.println("Error transforming shadow field: " + mixin.name + "." + field.name);
            System.exit(0);
        }
    }

    /**
     * looks through the instructions in the given method for any references to any field where its name matches oldName,
     * and its owner matched oldOwner. And changes it to point instead to newName, and newOwner. Then also for each field
     * transformed this way, if the type passed to this method is anything non-primitive, it will the insert a cast to
     * cast the field reference to that type.
     *
     * @param mn method to scan
     * @param oldType mixin type of the field
     * @param newType gamepack type of the field
     * @param oldName old name
     * @param oldOwner old owner
     * @param newName new name
     * @param newOwner new owner
     */
    private static void transformProxyField(JField field, MethodNode mn, Type oldType, Type newType, String oldName, String oldOwner, String newName, String newOwner)
    {
        InsnList instructions = mn.instructions;
        if (instructions == null) return;

        for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode)) continue;

            FieldInsnNode fin = (FieldInsnNode) insn;
            if (!fin.owner.equals(oldOwner) || !fin.name.equals(oldName)) continue;

            fin.owner = newOwner;
            fin.name  = newName;
            fin.desc = newType.getDescriptor();

            int op = fin.getOpcode();
            if ((op == Opcodes.GETFIELD || op == Opcodes.GETSTATIC))
            {
                if((oldType.getSort() == Type.OBJECT || oldType.getSort() == Type.ARRAY))
                {
                    TypeInsnNode checkcast = new TypeInsnNode(
                            Opcodes.CHECKCAST,
                            oldType.getInternalName()
                    );
                    instructions.insert(fin, checkcast);
                    continue;
                }
                if(field.getGetter() == null)
                    continue;

                Number multiplier = field.getGetter();

                if(field.getDescriptor().equals("I"))
                {
                    instructions.insert(fin, new LdcInsnNode(multiplier.intValue()));
                    instructions.insert(fin.getNext(), new InsnNode(Opcodes.IMUL));
                }
                else if(field.getDescriptor().equals("J"))
                {
                    instructions.insert(fin, new LdcInsnNode(multiplier.longValue()));
                    instructions.insert(fin.getNext(), new InsnNode(Opcodes.LMUL));
                }
            }
            else if (op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC) {
                if((oldType.getSort() == Type.OBJECT || oldType.getSort() == Type.ARRAY))
                {
                    TypeInsnNode checkcast = new TypeInsnNode(
                            Opcodes.CHECKCAST,
                            newType.getInternalName()
                    );
                    instructions.insertBefore(fin, checkcast);
                    continue;
                }
                if(field.getSetter() == null)
                    continue;

                Number multiplier = field.getSetter();

                if(field.getDescriptor().equals("I"))
                {
                    instructions.insertBefore(fin, new LdcInsnNode(multiplier.intValue()));
                    instructions.insertBefore(fin, new InsnNode(Opcodes.IMUL));
                }
                else if(field.getDescriptor().equals("J"))
                {
                    instructions.insertBefore(fin, new LdcInsnNode(multiplier.longValue()));
                    instructions.insertBefore(fin, new InsnNode(Opcodes.LMUL));
                }
            }
        }
    }
}
