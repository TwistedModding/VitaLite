package com.tonic.injector.pipeline;

import com.tonic.injector.util.TransformerUtil;
import com.tonic.util.ReflectBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Transforms ClassMod annotations to invoke direct class modification methods.
 */
public class ClassModTransformer
{
    /**
     * Invokes class modification method using reflection.
     * @param mixin mixin class containing modification method
     * @param method method annotated with @ClassMod
     * @throws ClassNotFoundException if mixin class cannot be loaded
     */
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
