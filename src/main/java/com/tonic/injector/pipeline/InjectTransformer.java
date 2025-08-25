package com.tonic.injector.pipeline;

import com.tonic.injector.util.FieldUtil;
import com.tonic.injector.util.MethodUtil;
import com.tonic.injector.util.TransformerUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class InjectTransformer
{
    public static void patch(ClassNode toClass, ClassNode fromClass, MethodNode method)
    {
        if(method.name.equals("<init>") && method.desc.equals("()V"))
            return;

        boolean methodExists = toClass.methods.stream()
                .anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc));

        if(methodExists)
            return;

        MethodNode copyMethod = MethodUtil.copyMethod(method, method.name, fromClass, toClass);
        toClass.methods.add(copyMethod);
    }

    /**
     * Injects a field from a mixin class into the gamepack class.
     *
     * @param gamepack the gamepack class node
     * @param field the field node to inject
     */
    public static void patch(ClassNode gamepack, FieldNode field)
    {
        boolean fieldExists = gamepack.fields.stream()
                .anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc));
        if(fieldExists)
            return;
        FieldNode copyField = FieldUtil.copyField(field);
        gamepack.fields.add(copyField);
    }
}
