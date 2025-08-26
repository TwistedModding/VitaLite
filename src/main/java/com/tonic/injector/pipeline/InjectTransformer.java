package com.tonic.injector.pipeline;

import com.tonic.injector.util.FieldUtil;
import com.tonic.injector.util.MethodUtil;
import com.tonic.injector.util.TransformerUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Injects methods and fields from mixin classes into gamepack classes.
 */
public class InjectTransformer
{
    /**
     * Injects a method from mixin to target class if it doesn't exist.
     *
     * @param toClass target class
     * @param fromClass source mixin class
     * @param method method to inject
     */
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
     * Injects a field into gamepack class if it doesn't exist.
     *
     * @param gamepack target class
     * @param field field to inject
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
