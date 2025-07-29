package com.tonic.injector.pipeline;

import com.tonic.injector.annotations.Shadow;
import com.tonic.util.AnnotationUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class ShadowTransformer
{
    public static void patch(ClassNode gamepack, ClassNode mixin, FieldNode field) {
        String oldOwner = mixin.name;
        String oldName = field.name;
        String newOwner = gamepack.name;
        String newName = AnnotationUtil.getAnnotation(field, Shadow.class, "name");
        Type newType = Type.getType((String)AnnotationUtil.getAnnotation(field, Shadow.class, "desc"));
        Type oldType = Type.getType(field.desc);

        for(MethodNode mn : mixin.methods)
        {
            transformProxyField(mn, oldType, newType, oldName, oldOwner, newName, newOwner);
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
    private static void transformProxyField(MethodNode mn, Type oldType, Type newType, String oldName, String oldOwner, String newName, String newOwner)
    {
        InsnList instructions = mn.instructions;
        if (instructions == null) return;

        for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof FieldInsnNode)) continue;

            FieldInsnNode fin = (FieldInsnNode) insn;
            // match owner + name
            if (!fin.owner.equals(oldOwner) || !fin.name.equals(oldName)) continue;

            // redirect to new field
            fin.owner = newOwner;
            fin.name  = newName;
            fin.desc = newType.getDescriptor();
            // descriptor stays the same (we assume mixin field.desc matches target)

            // if this is a field load and the mixin type is non-primitive, cast it
            int op = fin.getOpcode();
            if ((op == Opcodes.GETFIELD || op == Opcodes.GETSTATIC)
                    && (oldType.getSort() == Type.OBJECT || oldType.getSort() == Type.ARRAY))
            {
                TypeInsnNode checkcast = new TypeInsnNode(
                        Opcodes.CHECKCAST,
                        oldType.getInternalName()
                );
                instructions.insert(fin, checkcast);
            }
        }
    }
}
