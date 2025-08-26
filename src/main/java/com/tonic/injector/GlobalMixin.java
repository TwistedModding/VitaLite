package com.tonic.injector;

import com.tonic.injector.util.expreditor.impls.RuntimeMaxMemoryReplacer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class GlobalMixin
{
    private static final RuntimeMaxMemoryReplacer replacer = new RuntimeMaxMemoryReplacer(778502144L);

    public static void patch(ClassNode classNode)
    {
        for(MethodNode methodNode : classNode.methods)
        {
            replacer.instrument(classNode, methodNode);
        }
    }
}
