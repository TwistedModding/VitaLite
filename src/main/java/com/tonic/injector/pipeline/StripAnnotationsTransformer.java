package com.tonic.injector.pipeline;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Transformer that strips specific annotations from classes, fields, and methods.
 * Used for cleaning up injected gamepack classes for analysis purposes.
 */
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

    /**
     * Clears annotations that contain "Named" from the provided list.
     *
     * @param annotations the list of annotations to filter
     */
    private static void clearIfNonNull(List<AnnotationNode> annotations)
    {
        if(annotations != null)
        {
            annotations.removeIf(a -> a.desc.contains("Named"));
        }
    }
}