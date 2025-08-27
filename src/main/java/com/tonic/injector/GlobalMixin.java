package com.tonic.injector;

import com.tonic.injector.util.expreditor.impls.IntegerLiteralReplacer;
import com.tonic.injector.util.expreditor.impls.PathsGetReplacer;
import com.tonic.injector.util.expreditor.impls.RuntimeMaxMemoryReplacer;
import com.tonic.injector.util.expreditor.impls.SystemPropertyReplacer;
import org.objectweb.asm.tree.ClassNode;

public class GlobalMixin
{
    private static final RuntimeMaxMemoryReplacer memoryReplacer = new RuntimeMaxMemoryReplacer(778502144L);
    private static final SystemPropertyReplacer propertyReplacer = new SystemPropertyReplacer();
    private static final IntegerLiteralReplacer integerReplacer = new IntegerLiteralReplacer(-1094877034);
    private static final PathsGetReplacer pathsGetReplacer = new PathsGetReplacer();
    public static void patch(ClassNode classNode)
    {
        memoryReplacer.instrument(classNode);
        propertyReplacer.instrument(classNode);
        integerReplacer.instrument(classNode);
        pathsGetReplacer.instrument(classNode);
    }
}
