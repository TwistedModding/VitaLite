package com.tonic.injector.pipeline;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class StripAnnotationsTransformer
{
    public static void stripAnnotations(ClassNode cn)
    {
        clearIfNonNull(cn.visibleAnnotations);
        clearIfNonNull(cn.invisibleAnnotations);
        for(FieldNode fn : cn.fields)
        {
            clearIfNonNull(fn.visibleAnnotations);
            clearIfNonNull(fn.invisibleAnnotations);
        }
        for(MethodNode mn : cn.methods)
        {
            clearIfNonNull(mn.visibleAnnotations);
            clearIfNonNull(mn.invisibleAnnotations);
        }
    }

    private static void clearIfNonNull(List<AnnotationNode> annotations)
    {
        if(annotations != null)
        {
            annotations.removeIf(a -> a.desc.contains("Named"));
        }
    }
}