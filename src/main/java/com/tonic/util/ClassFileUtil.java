package com.tonic.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ClassFileUtil {

    /**
     * Writes {@code bytes} to {@code outputDir/<internalName>.class}.
     *
     * @param internalName e.g. "com/tonic/runelite/Install"
     * @param bytes        the classfile bytes from ASM (or equivalent)
     * @param outputDir    target directory (created if it doesn’t exist)
     * @throws IOException if writing fails
     */
    public static void writeClass(String internalName, byte[] bytes, Path outputDir)
            throws IOException {

        // build full file path  e.g.  <out>/com/tonic/runelite/Install.class
        Path classFile = outputDir.resolve(internalName + ".class");

        // create directories recursively
        Files.createDirectories(classFile.getParent());

        // write / overwrite
        Files.write(classFile,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    // utility class — no instances
    private ClassFileUtil() {}
}