package com.tonic.remapper.editor.analasys;
import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ITypeLoader;

import java.util.HashMap;
import java.util.Map;

/** Registers <internalName , byte[]> pairs and hands them to Procyon on demand. */
public final class InMemoryTypeLoader implements ITypeLoader {
    private final Map<String, byte[]> byInternalName = new HashMap<>();

    public void addType(String internalName, byte[] classBytes) {
        byInternalName.put(internalName, classBytes);
    }

    @Override
    public boolean tryLoadType(final String internalName, final Buffer buffer) {
        byte[] bytes = byInternalName.get(internalName);
        if (bytes == null) {
            return false;               // let the next loader try
        }
        buffer.putByteArray(bytes, 0, bytes.length);
        buffer.position(0);
        return true;
    }
}
