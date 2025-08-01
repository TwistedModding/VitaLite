package com.tonic.remap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

import java.util.HashSet;
import java.util.Set;

public class NormalizedField {
    public final FieldKey key;
    public final String typeDescriptor; // desc
    public final boolean isStatic;
    public final boolean isFinal;

    // method neighborhood (method keys) - these come from usage extractor
    public final Set<MethodKey> readers;
    public final Set<MethodKey> writers;

    public NormalizedField(String ownerInternalName, FieldNode fn,
                           Set<MethodKey> readers, Set<MethodKey> writers) {
        this.key = new FieldKey(ownerInternalName, fn.name, fn.desc);
        this.typeDescriptor = fn.desc;
        this.isStatic = (fn.access & Opcodes.ACC_STATIC) != 0;
        this.isFinal = (fn.access & Opcodes.ACC_FINAL) != 0;
        this.readers = readers != null ? new HashSet<>(readers) : new HashSet<>();
        this.writers = writers != null ? new HashSet<>(writers) : new HashSet<>();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
