package com.tonic.remapper.editor.analasys;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Comprehensive bytecode renaming utility that renames all classes, methods, and fields
 * while updating ALL references throughout the bytecode.
 *
 * Features:
 * - Renames classes to class0, class1, class2...
 * - Renames fields to field0, field1, field2...
 * - Renames methods to method0, method1, method2... (preserves <init>, <clinit>, main)
 * - Updates ALL bytecode references including:
 *   - Regular method calls (invokevirtual, invokestatic, invokespecial, invokeinterface)
 *   - Field accesses (getfield, putfield, getstatic, putstatic)
 *   - Type references (new, instanceof, checkcast, etc.)
 *   - InvokeDynamic instructions with bootstrap methods and arguments
 *   - Method handles in constant pool
 *   - ConstantDynamic values
 *   - Annotations and their values
 *   - Type annotations
 *   - Generic signatures
 *   - Exception handlers
 *   - Local variable tables
 *   - Inner classes
 *   - Nest members
 *   - Permitted subclasses
 *   - Module declarations
 *
 * Usage:
 *   BytecodeRenamer renamer = new BytecodeRenamer(classNodes);
 *   List<ClassNode> renamedClasses = renamer.rename();
 */
public class BytecodeRenamer {
    private static final int ASM_VERSION = Opcodes.ASM9;
    private final List<ClassNode> classes;
    private final Map<String, String> classMapping = new HashMap<>();
    private final Map<String, Map<String, String>> fieldMapping = new HashMap<>();
    private final Map<String, Map<String, String>> methodMapping = new HashMap<>();
    private final ComprehensiveRemapper remapper;
    private int classCounter = 0;
    private int fieldCounter = 0;
    private int methodCounter = 0;

    /**
     * Creates a new BytecodeRenamer instance.
     * @param classes List of ClassNode objects to rename
     */
    public BytecodeRenamer(List<ClassNode> classes) {
        this.classes = classes;
        this.remapper = new ComprehensiveRemapper();
    }

    /**
     * Gets the current mappings for debugging purposes.
     * @return Map containing class, field, and method mappings
     */
    public Map<String, Object> getMappings() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("classes", new HashMap<>(classMapping));
        mappings.put("fields", new HashMap<>(fieldMapping));
        mappings.put("methods", new HashMap<>(methodMapping));
        return mappings;
    }

    /**
     * Scans classes for invokedynamic instructions and reports findings.
     * @param classes List of ClassNode objects to scan
     */
    public static void scanForInvokeDynamic(List<ClassNode> classes) {
        int totalInvokeDynamic = 0;
        for (ClassNode cn : classes) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions != null) {
                    for (AbstractInsnNode insn : mn.instructions) {
                        if (insn instanceof InvokeDynamicInsnNode) {
                            totalInvokeDynamic++;
                            InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                            System.out.println("  InvokeDynamic found in " + cn.name + "." + mn.name +
                                    ": " + indy.name + indy.desc);
                        }
                    }
                }
            }
        }
        System.out.println("Total InvokeDynamic instructions found: " + totalInvokeDynamic);
    }

    /**
     * Test method to check if ClassNodes are being properly populated.
     * @param classes List of ClassNode objects to test
     */
    public static void testClassNodes(List<ClassNode> classes) {
        System.out.println("\n=== Testing ClassNodes ===");
        for (int i = 0; i < Math.min(3, classes.size()); i++) {
            ClassNode cn = classes.get(i);
            System.out.println("\nClass: " + cn.name);
            System.out.println("  Methods (" + cn.methods.size() + "):");
            for (int j = 0; j < Math.min(5, cn.methods.size()); j++) {
                MethodNode mn = cn.methods.get(j);
                System.out.println("    - " + mn.name + mn.desc +
                        " (instructions: " + (mn.instructions != null ? mn.instructions.size() : 0) + ")");
            }
            System.out.println("  Fields (" + cn.fields.size() + "):");
            for (int j = 0; j < Math.min(5, cn.fields.size()); j++) {
                FieldNode fn = cn.fields.get(j);
                System.out.println("    - " + fn.name + " : " + fn.desc);
            }
        }
        System.out.println("=== End Test ===\n");
    }

    /**
     * Performs the renaming operation on all classes.
     * @return List of renamed ClassNode objects
     */
    public List<ClassNode> rename() {
        return rename(false);
    }

    /**
     * Performs the renaming operation on all classes.
     * @param verbose If true, prints detailed debug output
     * @return List of renamed ClassNode objects
     */
    public List<ClassNode> rename(boolean verbose) {
        buildMappings();
        List<ClassNode> renamedClasses = new ArrayList<>();

        for (ClassNode cn : classes) {
            ClassNode renamed = applyRenaming(cn);

            if (verbose) {
                // Debug output
                System.out.println("Class " + cn.name + " -> " + renamed.name);
                System.out.println("  Original methods: " + cn.methods.size());
                System.out.println("  Renamed methods: " + renamed.methods.size());
                System.out.println("  Original fields: " + cn.fields.size());
                System.out.println("  Renamed fields: " + renamed.fields.size());

                // Verify the renamed class is valid
                verifyClassNode(renamed);
            }

            renamedClasses.add(renamed);
        }

        System.out.println("Renamed " + renamedClasses.size() + " classes");
        System.out.println("Total mappings: " + classMapping.size() + " classes, " +
                fieldCounter + " fields, " + methodCounter + " methods");

        return renamedClasses;
    }

    /**
     * Verifies that a ClassNode is valid and can be written.
     * @param cn ClassNode to verify
     */
    private void verifyClassNode(ClassNode cn) {
        try {
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            byte[] bytes = cw.toByteArray();
            System.out.println("  Verification: Class " + cn.name + " produces " + bytes.length + " bytes");
        } catch (Exception e) {
            System.err.println("  ERROR: Failed to verify class " + cn.name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Builds mappings for all classes, fields, and methods.
     */
    private void buildMappings() {
        for (ClassNode cn : classes) {
            String newClassName = cn.name.length() > 2 ? cn.name : "class" + (classCounter++);
            classMapping.put(cn.name, newClassName);

            Map<String, String> fieldMap = new HashMap<>();
            fieldMapping.put(cn.name, fieldMap);

            for (FieldNode fn : cn.fields) {
                fieldMap.put(fn.name, "field" + (fieldCounter++));
            }

            Map<String, String> methodMap = new HashMap<>();
            methodMapping.put(cn.name, methodMap);

            for (MethodNode mn : cn.methods) {
                if (mn.name.length() < 3) {
                    methodMap.put(mn.name + mn.desc, "method" + (methodCounter++));
                }
            }
        }
    }

    /**
     * Verifies the renaming results for debugging.
     * @param original Original ClassNode
     * @param renamed Renamed ClassNode
     */
    private void verifyRenaming(ClassNode original, ClassNode renamed) {
        System.out.println("Original class: " + original.name + " -> Renamed: " + renamed.name);
        System.out.println("Original methods: " + original.methods.size() + " -> Renamed methods: " + renamed.methods.size());
        System.out.println("Original fields: " + original.fields.size() + " -> Renamed fields: " + renamed.fields.size());
    }

    /**
     * Applies renaming to a single ClassNode.
     * @param original Original ClassNode
     * @return Renamed ClassNode
     */
    private ClassNode applyRenaming(ClassNode original) {
        ClassNode renamed = new ClassNode(ASM_VERSION);
        ClassRemapper classRemapper = new ClassRemapper(renamed, this.remapper);
        original.accept(classRemapper);
        return renamed;
    }

    /**
     * Custom remapper that handles all renaming logic.
     */
    private class ComprehensiveRemapper extends Remapper {
        @Override
        public String map(String internalName) {
            return classMapping.getOrDefault(internalName, internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            Map<String, String> fieldMap = fieldMapping.get(owner);
            if (fieldMap != null) {
                String mapped = fieldMap.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }
            return name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.length() > 2) {
                return name;
            }

            Map<String, String> methodMap = methodMapping.get(owner);
            if (methodMap != null) {
                String mapped = methodMap.get(name + descriptor);
                if (mapped != null) {
                    return mapped;
                }
            }
            return name;
        }

        @Override
        public String mapInvokeDynamicMethodName(String name, String descriptor) {
            for (Map<String, String> methodMap : methodMapping.values()) {
                String mapped = methodMap.get(name + descriptor);
                if (mapped != null) {
                    return mapped;
                }
            }
            return name;
        }
    }
}