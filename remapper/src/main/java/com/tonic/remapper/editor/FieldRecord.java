package com.tonic.remapper.editor;

import org.objectweb.asm.tree.FieldNode;

public class FieldRecord {
    public final FieldNode node;
    public String newName;
    final ClassMapping owner;
    FieldRecord(FieldNode fn, ClassMapping o)
    {
        node = fn;
        owner = o;
    }
}