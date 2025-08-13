package com.tonic.remapper.editor.analasys;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class AsmUtil
{
    public static String prettyPrint(MethodNode mn) {
        if(mn.invisibleAnnotations != null)
            mn.invisibleAnnotations.clear();
        Textifier printer = new Textifier();
        TraceMethodVisitor tmv = new TraceMethodVisitor(printer);
        mn.accept(tmv);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();          // free some memory
        return sw.toString();
    }
}
