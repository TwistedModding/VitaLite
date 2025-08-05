package com.tonic.remapper.editor.analasys;

import com.strobel.assembler.metadata.ITypeLoader;
import com.tonic.remapper.dto.JClass;
import com.tonic.remapper.editor.ClassMapping;
import com.tonic.remapper.methods.MethodKey;
import com.tonic.remapper.methods.UsedMethodScanner;
import com.tonic.remapper.misc.ProgressBar;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import java.nio.file.Paths;
import java.util.*;

public class DeobDump
{
    public static void dump(Map<String, ClassMapping> classMappings, List<JClass> mappings, String outputDirectory)
    {
        System.out.println("Mapping classes...");
        List<ClassNode> classesObfu = new ArrayList<>();
        for (ClassMapping mapping : classMappings.values())
        {
            ClassNode classNode = mapping.classNode;
            if (classNode != null && !classNode.name.contains("/"))
            {
                classesObfu.add(classNode);
            }
        }

        System.out.println("Renaming classes...");
        BytecodeRenamer.scanForInvokeDynamic(classesObfu);
        BytecodeRenamer renamer = new BytecodeRenamer(classesObfu, mappings);
        List<ClassNode> classes = renamer.rename();

        System.out.println("Deobfuscating classes...");
        for(ClassNode classNode : classes)
        {
            preprocessClassNode(classNode);
            for(FieldNode fieldNode : classNode.fields)
            {
                if(fieldNode.invisibleAnnotations != null)
                    fieldNode.invisibleAnnotations.clear();
            }
            for(MethodNode methodNode : classNode.methods)
            {
                if(methodNode.invisibleAnnotations != null)
                    methodNode.invisibleAnnotations.clear();
                try {
                    DeobPipeline.create()
                            //.add(BytecodeTransformers.constantFolding())
                            //.add(BytecodeTransformers.deadCodeElimination())
                            //.add(BytecodeTransformers.fixLabeledBreaks())
                            .add(BytecodeTransformers.stripTryCatch())
                            .run(methodNode);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        System.out.println("Decompiling classes...");
        Map<String,String> sources = BatchDecompiler.decompile(classes, true);
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            try {
                String cleaned = SpoonPipeline.create()
                        .add(new SpoonPipeline.OpaquePredicateCleaner())
                        .run(entry.getKey(), entry.getValue());
                sources.put(entry.getKey(), cleaned);
            } catch (Exception ex) {
                // Keep original source if Spoon fails
                System.err.println("Spoon processing failed for " + entry.getKey() + ": " + ex.getMessage());
            }
        }
        //write class sources to directory
        System.out.println("Writing sources to " + outputDirectory);
        for(var entry : sources.entrySet())
        {
            String className = entry.getKey();
            String source = entry.getValue();
            String fileName = className.replace('/', '.') + ".java";
            try {
                java.nio.file.Files.write(Paths.get(outputDirectory, fileName), source.getBytes());
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    //class cleaning
    private static void preprocessClassNode(ClassNode classNode) {
        // Remove synthetic access flags that confuse decompilers
        classNode.access &= ~Opcodes.ACC_SYNTHETIC;
        classNode.access &= ~Opcodes.ACC_BRIDGE;

        // Fix method access flags
        if (classNode.methods != null) {
            for (MethodNode method : classNode.methods) {
                // Remove problematic flags
                method.access &= ~Opcodes.ACC_SYNTHETIC;
                method.access &= ~Opcodes.ACC_BRIDGE;

                // Fix constructor names if needed
                if ("<init>".equals(method.name)) {
                    method.access &= ~Opcodes.ACC_STATIC;
                }
            }
        }
    }
}
