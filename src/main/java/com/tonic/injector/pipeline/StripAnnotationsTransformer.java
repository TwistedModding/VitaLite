package com.tonic.injector.pipeline;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class StripAnnotationsTransformer
{
    /**
     * Strips annotations that contain "Named" from the class, fields, and methods.
     * This is used for when you dump the injected gamepack for analysis.
     *
     * @param cn the ClassNode to process
     */
    public static void stripAnnotations(ClassNode cn)
    {
        clearIfNonNull(cn.invisibleAnnotations);
        for(FieldNode fn : cn.fields)
        {
            clearIfNonNull(fn.invisibleAnnotations);
        }
        for(MethodNode mn : cn.methods)
        {
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