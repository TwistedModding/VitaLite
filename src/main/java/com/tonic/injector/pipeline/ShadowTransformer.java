package com.tonic.injector.pipeline;

import com.tonic.dto.JClass;
import com.tonic.dto.JField;
import com.tonic.injector.MappingProvider;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;

public class ShadowTransformer
{
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
