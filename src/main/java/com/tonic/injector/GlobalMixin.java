package com.tonic.injector;

import com.tonic.injector.util.expreditor.impls.*;
import org.objectweb.asm.tree.ClassNode;

public class GlobalMixin
{
    private static final RuntimeMaxMemoryReplacer memoryReplacer = new RuntimeMaxMemoryReplacer(778502144L);
    private static final SystemPropertyReplacer propertyReplacer = new SystemPropertyReplacer();
    private static final IntegerLiteralReplacer integerReplacer = new IntegerLiteralReplacer(-1094877034);
    private static final PathsGetReplacer pathsGetReplacer = new PathsGetReplacer();
    private static final ReplaceMethodByString replaceMethodByString = new ReplaceMethodByString("Attempted to load patches of already loading midiplayer!");
    private static final ModifyResourceLoading modifyResourceLoading = new ModifyResourceLoading();
    public static void patch(ClassNode classNode)
    {
        memoryReplacer.instrument(classNode);
        propertyReplacer.instrument(classNode);
        integerReplacer.instrument(classNode);
        pathsGetReplacer.instrument(classNode);
        replaceMethodByString.instrument(classNode);
        //modifyResourceLoading.instrument(classNode);
    }
}
