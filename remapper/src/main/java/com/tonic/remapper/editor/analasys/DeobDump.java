package com.tonic.remapper.editor.analasys;

import com.strobel.assembler.metadata.ITypeLoader;
import com.tonic.remapper.editor.ClassMapping;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeobDump
{
    public static void dump(Map<String, ClassMapping> classMappings, String outputDirectory)
    {
        List<ClassNode> classes = new ArrayList<>();
        for (ClassMapping mapping : classMappings.values())
        {
            ClassNode classNode = mapping.classNode;
            if (classNode != null && !classNode.name.contains("/"))
            {
                classes.add(classNode);
            }
        }

        for(ClassNode classNode : classes)
        {
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
                    //DeobPipeline.create()
                            //.add(BytecodeTransformers.constantFolding())
                            //.add(BytecodeTransformers.deadCodeElimination())
                            //.add(BytecodeTransformers.stripTryCatch())
                            //.run(methodNode);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        Map<String,String> sources = new HashMap<>();
        for(ClassNode classNode : classes)
        {
            String source = DecompilerUtil.decompile(classNode);

            try {
                source = SpoonPipeline.create()
                        .add(new SpoonPipeline.OpaquePredicateCleaner())
                        .run(classNode.name, source);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);
            }
            sources.put(classNode.name, source);
        }
        //write class sources to directory
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
}
