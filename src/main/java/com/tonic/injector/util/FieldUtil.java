package com.tonic.injector.util;

import org.objectweb.asm.tree.*;
import java.util.ArrayList;

public class FieldUtil {
    /**
     * Creates a deep copy of a FieldNode
     */
    public static FieldNode copyField(FieldNode original) {
        FieldNode copy = new FieldNode(
                original.access,
                original.name,
                original.desc,
                original.signature,
                original.value
        );

        if (original.attrs != null) {
            copy.attrs = new ArrayList<>(original.attrs);
        }

        return copy;
    }
}
