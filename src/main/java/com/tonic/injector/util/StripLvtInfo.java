package com.tonic.injector.util;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class StripLvtInfo
{
    public static void run(ClassNode classNode)
    {
        for(MethodNode methodNode : classNode.methods)
        {
            methodNode.localVariables = null;
        }
    }
}
