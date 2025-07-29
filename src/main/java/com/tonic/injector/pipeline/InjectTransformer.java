package com.tonic.injector.pipeline;

import com.tonic.util.FieldUtil;
import com.tonic.util.MethodUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class InjectTransformer
{
    public static void patch(ClassNode gamepack, ClassNode mixin, MethodNode method)
    {
        if(method.name.equals("<init>") && method.desc.equals("()V"))
            return;
        MethodNode copyMethod = MethodUtil.copyMethod(method, method.name, mixin, gamepack);
        gamepack.methods.add(copyMethod);
    }

    public static void patch(ClassNode gamepack, FieldNode field)
    {
        FieldNode copyField = FieldUtil.copyField(field);
        gamepack.fields.add(copyField);
    }
}
