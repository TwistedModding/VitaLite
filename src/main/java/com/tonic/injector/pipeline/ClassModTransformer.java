package com.tonic.injector.pipeline;

import com.tonic.injector.util.TransformerUtil;
import com.tonic.util.ReflectBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassModTransformer
{
    public static void patch(ClassNode mixin, MethodNode method) throws ClassNotFoundException {
        ClassNode target = TransformerUtil.getBaseClass(mixin);
        String name = mixin.name.replace("/", ".");
        Class<?> clazz = InsertTransformer.class.getClassLoader()
                .loadClass(name);
        ReflectBuilder.of(clazz)
                .staticMethod(
                        method.name,
                        new Class[]{ClassNode.class},
                        new Object[]{target}
                )
                .get();
    }
}
