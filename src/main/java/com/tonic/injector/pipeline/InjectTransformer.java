package com.tonic.injector.pipeline;

import com.tonic.util.FieldUtil;
import com.tonic.util.MethodUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class InjectTransformer
{
    /**
     * Injects a method from a mixin class into the gamepack class.
     *
     * @param gamepack the gamepack class node
     * @param mixin the mixin class node
     * @param method the method node to inject
     */
    public static void patch(ClassNode gamepack, ClassNode mixin, MethodNode method)
    {
        if(method.name.equals("<init>") && method.desc.equals("()V"))
            return;
        MethodNode copyMethod = MethodUtil.copyMethod(method, method.name, mixin, gamepack);
        gamepack.methods.add(copyMethod);
    }

    /**
     * Injects a field from a mixin class into the gamepack class.
     *
     * @param gamepack the gamepack class node
     * @param field the field node to inject
     */
    public static void patch(ClassNode gamepack, FieldNode field)
    {
        FieldNode copyField = FieldUtil.copyField(field);
        gamepack.fields.add(copyField);
    }
}
