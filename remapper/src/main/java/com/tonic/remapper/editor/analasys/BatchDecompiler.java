package com.tonic.remapper.editor.analasys;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class BatchDecompiler {

    private static final boolean DEBUG = true;

    public static Map<String, String> decompile(List<ClassNode> classes) {
        return decompile(classes, false);
    }

    public static Map<String, String> decompile(List<ClassNode> classes, boolean parallel) {
        Map<String, String> results = new ConcurrentHashMap<>();

        try {
            // Create temporary directories
            Path tempInput = Files.createTempDirectory("fernflower_input");
            Path tempOutput = Files.createTempDirectory("fernflower_output");

            // Create a map for quick lookup
            Map<String, ClassNode> classNodeMap = new HashMap<>();
            for (ClassNode cn : classes) {
                classNodeMap.put(cn.name, cn);
            }

            // Write all classes to temp directory
            Map<String, Path> classFiles = new HashMap<>();
            for (ClassNode classNode : classes) {
                try {
                    ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES, classNodeMap);
                    classNode.accept(cw);
                    byte[] bytes = cw.toByteArray();

                    // Create directory structure
                    String className = classNode.name;
                    Path classFile;
                    if (className.contains("/")) {
                        Path packageDir = tempInput.resolve(className.substring(0, className.lastIndexOf('/')));
                        Files.createDirectories(packageDir);
                        classFile = tempInput.resolve(className + ".class");
                    } else {
                        classFile = tempInput.resolve(className + ".class");
                    }

                    Files.write(classFile, bytes);
                    classFiles.put(className, classFile);
                } catch (Exception e) {
                    System.err.println("Failed to write class file for " + classNode.name + ": " + e.getMessage());
                    // Generate skeleton for failed classes
                    StringWriter sw = new StringWriter();
                    sw.append("// Failed to compile class for decompilation: ").append(e.getMessage()).append("\n");
                    generateClassSkeleton(classNode, sw);
                    results.put(classNode.name, sw.toString());
                }
            }

            // Configure FernFlower
            Map<String, Object> options = new HashMap<>();
            options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
            options.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
            options.put(IFernflowerPreferences.DECOMPILE_ENUM, "1");
            options.put(IFernflowerPreferences.DECOMPILE_ASSERTIONS, "1");
            options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
            options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
            options.put(IFernflowerPreferences.HIDE_EMPTY_SUPER, "1");
            options.put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "1");
            options.put(IFernflowerPreferences.FINALLY_DEINLINE, "1");
            options.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "60"); // Timeout for complex methods
            options.put(IFernflowerPreferences.RENAME_ENTITIES, "0"); // Don't rename
            options.put(IFernflowerPreferences.USER_RENAMER_CLASS, ""); // No renaming

            // ADD THESE FOR BETTER HANDLING OF OBFUSCATED CODE:
            options.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "1");
            options.put(IFernflowerPreferences.BOOLEAN_TRUE_ONE, "1");
            options.put(IFernflowerPreferences.SYNTHETIC_NOT_SET, "1");
            options.put(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT, "1");
            options.put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1");
            options.put(IFernflowerPreferences.USE_METHOD_PARAMETERS, "1");
            options.put(IFernflowerPreferences.LITERALS_AS_IS, "0");
            options.put(IFernflowerPreferences.UNIT_TEST_MODE, "0");

            // For handling weird constructor patterns:
            options.put(IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1");
            options.put(IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "0");

            // Increase timeout for complex methods
            options.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "10000"); // Increase from 60

            // Better variable naming
            options.put(IFernflowerPreferences.REMOVE_EMPTY_RANGES, "1");
            options.put(IFernflowerPreferences.ENSURE_SYNCHRONIZED_MONITOR, "1");

            // Pattern matching improvements
            options.put(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION, "0");
            options.put(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS, "0");
            options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "0");
            options.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
            options.put(IFernflowerPreferences.TRY_LOOP_FIX, "1");

            // Create custom decompiler
            FernFlowerDecompiler decompiler = new FernFlowerDecompiler(tempOutput, options);

            // Decompile all classes
            System.out.println("Decompiling " + classFiles.size() + " classes with FernFlower...");
            decompiler.addSource(tempInput.toFile());
            decompiler.decompileContext();

            // Read decompiled sources
            for (Map.Entry<String, Path> entry : classFiles.entrySet()) {
                String className = entry.getKey();
                String javaFileName = className + ".java";
                Path javaFile = tempOutput.resolve(javaFileName);

                if (Files.exists(javaFile)) {
                    try {
                        String source = Files.readString(javaFile);
                        results.put(className, source);
                    } catch (IOException e) {
                        System.err.println("Failed to read decompiled source for " + className);
                        ClassNode cn = classNodeMap.get(className);
                        if (cn != null) {
                            StringWriter sw = new StringWriter();
                            sw.append("// Failed to read decompiled source\n");
                            generateClassSkeleton(cn, sw);
                            results.put(className, sw.toString());
                        }
                    }
                } else {
                    // FernFlower failed to decompile this class
                    System.err.println("FernFlower failed to decompile " + className);
                    ClassNode cn = classNodeMap.get(className);
                    if (cn != null) {
                        StringWriter sw = new StringWriter();
                        sw.append("// FernFlower failed to decompile this class\n");
                        generateClassSkeleton(cn, sw);
                        results.put(className, sw.toString());
                    }
                }
            }

            // Cleanup temp directories
            deleteDirectory(tempInput);
            deleteDirectory(tempOutput);

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: generate skeletons for all classes
            for (ClassNode classNode : classes) {
                if (!results.containsKey(classNode.name)) {
                    StringWriter sw = new StringWriter();
                    sw.append("// Decompilation failed: ").append(e.getMessage()).append("\n");
                    generateClassSkeleton(classNode, sw);
                    results.put(classNode.name, sw.toString());
                }
            }
        }

        return results;
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private static void generateClassSkeleton(ClassNode classNode, StringWriter out) {
        out.append("/* \n");
        out.append(" * This class could not be fully decompiled.\n");
        out.append(" * Only the class structure is shown below.\n");
        out.append(" */\n\n");

        // Add package if present
        if (classNode.name.contains("/")) {
            String packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/')).replace('/', '.');
            out.append("package ").append(packageName).append(";\n\n");
        }

        // Access modifiers
        String modifiers = Modifier.toString(classNode.access & ~Opcodes.ACC_SUPER);
        if (!modifiers.isEmpty()) {
            out.append(modifiers).append(" ");
        }

        // Class/interface/enum
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            out.append("interface ");
        } else if ((classNode.access & Opcodes.ACC_ENUM) != 0) {
            out.append("enum ");
        } else {
            out.append("class ");
        }

        out.append(getSimpleClassName(classNode.name));

        // Extends
        if (classNode.superName != null && !classNode.superName.equals("java/lang/Object")) {
            out.append(" extends ").append(classNode.superName.replace('/', '.'));
        }

        // Implements
        if (classNode.interfaces != null && !classNode.interfaces.isEmpty()) {
            out.append(" implements ");
            for (int i = 0; i < classNode.interfaces.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(classNode.interfaces.get(i).replace('/', '.'));
            }
        }

        out.append(" {\n");

        // Fields
        if (classNode.fields != null) {
            for (FieldNode field : classNode.fields) {
                out.append("    ");
                String fieldModifiers = Modifier.toString(field.access);
                if (!fieldModifiers.isEmpty()) {
                    out.append(fieldModifiers).append(" ");
                }
                out.append(Type.getType(field.desc).getClassName());
                out.append(" ").append(field.name).append(";\n");
            }
            if (!classNode.fields.isEmpty()) out.append("\n");
        }

        // Methods
        if (classNode.methods != null) {
            for (MethodNode method : classNode.methods) {
                out.append("    ");
                String methodModifiers = Modifier.toString(method.access & ~Opcodes.ACC_SYNCHRONIZED);
                if (!methodModifiers.isEmpty()) {
                    out.append(methodModifiers).append(" ");
                }

                Type methodType = Type.getMethodType(method.desc);
                out.append(methodType.getReturnType().getClassName());
                out.append(" ").append(method.name).append("(");

                Type[] argTypes = methodType.getArgumentTypes();
                for (int i = 0; i < argTypes.length; i++) {
                    if (i > 0) out.append(", ");
                    out.append(argTypes[i].getClassName()).append(" arg").append(i + "");
                }

                out.append(") {\n");
                out.append("        // Method body could not be decompiled\n");
                out.append("    }\n\n");
            }
        }

        out.append("}\n");
    }

    private static String getSimpleClassName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    /**
     * Custom FernFlower decompiler wrapper
     */
    private static class FernFlowerDecompiler extends ConsoleDecompiler {
        public FernFlowerDecompiler(Path destination, Map<String, Object> options) {
            super(destination.toFile(), options, new PrintStreamLogger(System.out));
        }
    }

    /**
     * SafeClassWriter that handles missing types more gracefully
     */
    private static class SafeClassWriter extends ClassWriter {
        private final Map<String, ClassNode> classNodeMap;

        public SafeClassWriter(int flags, Map<String, ClassNode> classNodeMap) {
            super(flags);
            this.classNodeMap = classNodeMap;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            if (type1.equals(type2)) {
                return type1;
            }

            if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                return "java/lang/Object";
            }

            // Check our class map first
            ClassNode node1 = classNodeMap.get(type1);
            ClassNode node2 = classNodeMap.get(type2);

            if (node1 != null && node2 != null) {
                // Try to find common super from our nodes
                String super1 = node1.superName;
                String super2 = node2.superName;

                if (super1 != null && super1.equals(type2)) {
                    return type2;
                }
                if (super2 != null && super2.equals(type1)) {
                    return type1;
                }
            }

            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Exception e) {
                // If we can't determine the common superclass, use Object
                if (DEBUG) {
                    System.err.println("Cannot find common super for: " + type1 + " and " + type2);
                }
                return "java/lang/Object";
            }
        }
    }
}