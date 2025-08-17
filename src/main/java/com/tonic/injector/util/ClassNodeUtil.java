package com.tonic.injector.util;

import com.tonic.vitalite.Main;
import com.tonic.injector.types.GamepackClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class ClassNodeUtil {
    public static byte[] toBytes(ClassNode classNode) {
        ClassWriter classWriter = new GamepackClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, Main.CTX_CLASSLOADER) ;
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    public static ClassNode toNode(byte[] classBytes) {
        ClassReader classReader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }
}
