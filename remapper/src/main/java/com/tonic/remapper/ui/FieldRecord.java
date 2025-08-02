package com.tonic.remapper.ui;

import org.objectweb.asm.tree.FieldNode;

public class FieldRecord {
    final FieldNode node;
    String newName;
    final ClassMapping owner;
    FieldRecord(FieldNode fn, ClassMapping o) { node = fn; owner = o; }
}