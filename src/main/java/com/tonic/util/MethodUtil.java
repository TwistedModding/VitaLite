package com.tonic.util;

import com.tonic.injector.types.CopyMethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodUtil
{

    /**
     * Copy a method with a new name
     */
    public static MethodNode copyMethod(MethodNode original, String newName, ClassNode mixinClass, ClassNode gamepackClass) {
        MethodNode copy = new MethodNode(
                original.access,
                newName,
                original.desc,
                original.signature,
                original.exceptions.toArray(new String[0])
        );

        // Copy instructions with transformation
        original.accept(new CopyMethodVisitor(Opcodes.ASM9, copy, mixinClass, gamepackClass));

        // Update the name
        copy.name = newName;

        return copy;
    }
}
