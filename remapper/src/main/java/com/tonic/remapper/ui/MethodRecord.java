package com.tonic.remapper.ui;

import org.objectweb.asm.tree.MethodNode;

public class MethodRecord {
    public final MethodNode node;
    public String newName;
    final ClassMapping owner;
    MethodRecord(MethodNode mn, ClassMapping o) { node = mn; owner = o; }
}