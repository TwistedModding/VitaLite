package com.tonic.injector;

import com.tonic.injector.util.expreditor.ArrayAccess;
import com.tonic.injector.util.expreditor.ExprEditor;
import com.tonic.injector.util.expreditor.impls.RuntimeMaxMemoryReplacer;
import com.tonic.injector.util.expreditor.impls.SystemPropertyReplacer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class GlobalMixin
{
    private static final RuntimeMaxMemoryReplacer memoryReplacer = new RuntimeMaxMemoryReplacer(778502144L);
    private static final SystemPropertyReplacer propertyReplacer = new SystemPropertyReplacer();
    public static void patch(ClassNode classNode)
    {
        for(MethodNode methodNode : classNode.methods)
        {
            memoryReplacer.instrument(classNode, methodNode);
            propertyReplacer.instrument(classNode, methodNode);
        }
    }
}
