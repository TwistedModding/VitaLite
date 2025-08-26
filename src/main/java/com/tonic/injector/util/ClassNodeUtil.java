package com.tonic.injector.util;

import com.tonic.vitalite.Main;
import com.tonic.injector.types.GamepackClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ClassNodeUtil {
    public static byte[] toBytes(ClassNode classNode) {
        try
        {
            ClassWriter classWriter = new GamepackClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, Main.CTX_CLASSLOADER) ;
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.out.println("Class: " + classNode.name);
            System.exit(1);
        }
        return null;
    }

    public static String prettyPrint(MethodNode mn) {
        if(mn.invisibleAnnotations != null)
            mn.invisibleAnnotations.clear();
        Textifier printer = new Textifier();
        TraceMethodVisitor tmv = new TraceMethodVisitor(printer);
        mn.accept(tmv);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString();
    }

    public static ClassNode toNode(byte[] classBytes) {
        ClassReader classReader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }
}
