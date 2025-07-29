package com.tonic.util;


import com.tonic.Main;
import java.io.*;
import java.util.HashMap;
import java.util.jar.*;

public class JarDumper
{
    public static void dump(HashMap<String, byte[]> classes) throws IOException {
        String outputPath = Main.optionsParser.getRsDumpPath();
        if(outputPath == null || !new File(outputPath).exists())
            return;
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputPath))) {
            for (HashMap.Entry<String, byte[]> entry : classes.entrySet()) {
                String className = entry.getKey();
                byte[] classBytes = entry.getValue();
                String entryName = className.replace('.', '/') + ".class";
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(classBytes);
                jos.closeEntry();
            }
        }
        System.out.println("Dumped " + classes.size() + " classes to " + outputPath);
    }
}
