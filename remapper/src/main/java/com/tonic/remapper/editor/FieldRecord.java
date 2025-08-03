package com.tonic.remapper.editor;

import org.objectweb.asm.tree.FieldNode;

public class FieldRecord {
    public final FieldNode node;
    public String newName;
    public Number  setter;
    public Number  getter;
    final ClassMapping owner;
    FieldRecord(FieldNode fn, ClassMapping o)
    {
        node = fn;
        owner = o;
    }
}