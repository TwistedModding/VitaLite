package com.tonic.remapper.editor.analasys;

import com.tonic.remapper.dto.JClass;
import com.tonic.remapper.dto.JField;
import com.tonic.remapper.dto.JMethod;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Comprehensive bytecode renaming utility that renames all classes, methods, and fields
 * while updating ALL references throughout the bytecode.
 *
 * Features:
 * - Renames classes to class0, class1, class2... (only if original name is 2 chars or less)
 * - Renames fields to field0, field1, field2...
 * - Renames methods to method0, method1, method2... (preserves <init>, <clinit>, main)
 * - Adds @ObfuscatedName annotations to preserve original names and descriptors
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
 * The @ObfuscatedName annotation is expected to have this structure:
 *   @Retention(RetentionPolicy.RUNTIME)
 *   @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
 *   public @interface ObfuscatedName {
 *       String obfuscatedName();
 *       String descriptor() default "";
 *   }
 *
 * Usage:
 *   BytecodeRenamer renamer = new BytecodeRenamer(classNodes);
 *   List<ClassNode> renamedClasses = renamer.rename();
 */
public class BytecodeRenamer {
    private static final int ASM_VERSION = Opcodes.ASM9;
    private static final String DEFAULT_ANNOTATION_DESCRIPTOR = "Lcom/tonic/remapper/ObfuscatedName;";
    private final List<ClassNode> classes;
    private final Map<String, String> classMapping = new HashMap<>();
    private final Map<String, Map<String, String>> fieldMapping = new HashMap<>();
    private final Map<String, Map<String, String>> methodMapping = new HashMap<>();
    private final List<JClass> mappings;
    private final ComprehensiveRemapper remapper;
    private final String annotationDescriptor;
    private int classCounter = 0;
    private int fieldCounter = 0;
    private int methodCounter = 0;

    /**
     * Creates a new BytecodeRenamer instance with default annotation descriptor.
     * @param classes List of ClassNode objects to rename
     */
    public BytecodeRenamer(List<ClassNode> classes, List<JClass> mappings) {
        this(classes, mappings, DEFAULT_ANNOTATION_DESCRIPTOR);
    }

    /**
     * Creates a new BytecodeRenamer instance with custom annotation descriptor.
     * @param classes List of ClassNode objects to rename
     * @param annotationDescriptor The descriptor for the ObfuscatedName annotation (e.g., "Lcom/example/ObfuscatedName;")
     */
    public BytecodeRenamer(List<ClassNode> classes, List<JClass> mappings, String annotationDescriptor) {
        this.classes = classes;
        this.annotationDescriptor = annotationDescriptor;
        this.remapper = new ComprehensiveRemapper();
        this.mappings = mappings;
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
     * Retrieves the original obfuscated name from an annotated element.
     * @param annotations List of annotation nodes
     * @param annotationDescriptor The descriptor of the ObfuscatedName annotation
     * @return The original name, or null if not found
     */
    public static String getOriginalName(List<AnnotationNode> annotations, String annotationDescriptor) {
        if (annotations == null) return null;

        for (AnnotationNode an : annotations) {
            if (an.desc.equals(annotationDescriptor)) {
                if (an.values != null) {
                    for (int i = 0; i < an.values.size(); i += 2) {
                        if ("obfuscatedName".equals(an.values.get(i))) {
                            return (String) an.values.get(i + 1);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Retrieves the original descriptor from an annotated method or field.
     * @param annotations List of annotation nodes
     * @param annotationDescriptor The descriptor of the ObfuscatedName annotation
     * @return The original descriptor, or null if not found
     */
    public static String getOriginalDescriptor(List<AnnotationNode> annotations, String annotationDescriptor) {
        if (annotations == null) return null;

        for (AnnotationNode an : annotations) {
            if (an.desc.equals(annotationDescriptor)) {
                if (an.values != null) {
                    for (int i = 0; i < an.values.size(); i += 2) {
                        if ("descriptor".equals(an.values.get(i))) {
                            return (String) an.values.get(i + 1);
                        }
                    }
                }
            }
        }
        return null;
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
     * Only renames classes with names of 2 characters or less.
     */
    private void buildMappings() {
        JClass owner;
        for (ClassNode cn : classes) {
            owner = findCLass(cn.name);
            // Only rename classes with names of 2 characters or less
            if (cn.name.length() <= 2) {
                String newClassName;
                if(owner == null || owner.getName() == null || owner.getName().isBlank())
                    newClassName = "class" + (classCounter++);
                else
                    newClassName = owner.getName();
                classMapping.put(cn.name, newClassName);
            }

            Map<String, String> fieldMap = new HashMap<>();
            fieldMapping.put(cn.name, fieldMap);

            for (FieldNode fn : cn.fields) {
                String fieldName;
                JField field = findField(owner, fn.name);
                if (field == null || field.getName() == null || field.getName().isBlank())
                    fieldName = "field" + (fieldCounter++);
                else
                    fieldName = field.getName();
                fieldMap.put(fn.name, fieldName);
            }

            Map<String, String> methodMap = new HashMap<>();
            methodMapping.put(cn.name, methodMap);

            for (MethodNode mn : cn.methods) {
                if (!mn.name.equals("<init>") && !mn.name.equals("<clinit>") && !mn.name.equals("main")) {
                    String methodName;
                    JMethod method = findMethod(owner, mn.name, mn.desc);
                    if (method == null || method.getName() == null || method.getName().isBlank())
                        methodName = "method" + (methodCounter++);
                    else
                        methodName = method.getName();
                    methodMap.put(mn.name + mn.desc, methodName);
                }
            }
        }
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

        // Add @ObfuscatedName annotation to the class if it was renamed
        if (classMapping.containsKey(original.name)) {
            addClassAnnotation(renamed, original.name);
        }

        // Add @ObfuscatedName annotations to fields and methods
        addFieldAndMethodAnnotations(renamed, original);

        return renamed;
    }

    /**
     * Adds @ObfuscatedName annotation to a class.
     * @param classNode The class node to annotate
     * @param originalName The original obfuscated name
     */
    private void addClassAnnotation(ClassNode classNode, String originalName) {
        if (classNode.visibleAnnotations == null) {
            classNode.visibleAnnotations = new ArrayList<>();
        }

        AnnotationNode annotation = new AnnotationNode(annotationDescriptor);
        annotation.values = new ArrayList<>();
        annotation.values.add("obfuscatedName");
        annotation.values.add(originalName);

        classNode.visibleAnnotations.add(annotation);
    }

    /**
     * Adds @ObfuscatedName annotations to fields and methods.
     * @param renamed The renamed class node
     * @param original The original class node
     */
    private void addFieldAndMethodAnnotations(ClassNode renamed, ClassNode original) {
        // Annotate fields
        Map<String, String> fieldMap = fieldMapping.get(original.name);
        if (fieldMap != null) {
            for (int i = 0; i < original.fields.size(); i++) {
                FieldNode originalField = original.fields.get(i);
                FieldNode renamedField = renamed.fields.get(i);

                if (fieldMap.containsKey(originalField.name)) {
                    if (renamedField.visibleAnnotations == null) {
                        renamedField.visibleAnnotations = new ArrayList<>();
                    }

                    AnnotationNode annotation = new AnnotationNode(annotationDescriptor);
                    annotation.values = new ArrayList<>();
                    annotation.values.add("obfuscatedName");
                    annotation.values.add(originalField.name);
                    annotation.values.add("descriptor");
                    annotation.values.add(originalField.desc);

                    renamedField.visibleAnnotations.add(annotation);
                }
            }
        }

        // Annotate methods
        Map<String, String> methodMap = methodMapping.get(original.name);
        if (methodMap != null) {
            for (int i = 0; i < original.methods.size(); i++) {
                MethodNode originalMethod = original.methods.get(i);
                MethodNode renamedMethod = renamed.methods.get(i);

                if (methodMap.containsKey(originalMethod.name + originalMethod.desc)) {
                    if (renamedMethod.visibleAnnotations == null) {
                        renamedMethod.visibleAnnotations = new ArrayList<>();
                    }

                    AnnotationNode annotation = new AnnotationNode(annotationDescriptor);
                    annotation.values = new ArrayList<>();
                    annotation.values.add("obfuscatedName");
                    annotation.values.add(originalMethod.name);
                    annotation.values.add("descriptor");
                    annotation.values.add(originalMethod.desc);

                    renamedMethod.visibleAnnotations.add(annotation);
                }
            }
        }
    }

    /**
     * Custom remapper that handles all renaming logic including invokedynamic.
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
            if (name.equals("<init>") || name.equals("<clinit>") || name.equals("main")) {
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
            // For invokedynamic, we need to check all classes since the method could be anywhere
            for (Map<String, String> methodMap : methodMapping.values()) {
                String mapped = methodMap.get(name + descriptor);
                if (mapped != null) {
                    return mapped;
                }
            }
            return name;
        }

        @Override
        public String mapPackageName(String name) {
            return name;
        }

        @Override
        public String mapModuleName(String name) {
            return name;
        }
    }

    private JClass findCLass(String obfuName)
    {
        if(mappings == null)
            return null;
        for (JClass jClass : mappings) {
            if (jClass.getObfuscatedName().equals(obfuName)) {
                return jClass;
            }
        }
        return null;
    }

    private JMethod findMethod(JClass owner, String obfuName, String desc) {
        if(owner == null) {
            return null;
        }
        for (JMethod method : owner.getMethods()) {
            if (method.getObfuscatedName().equals(obfuName) && method.getDescriptor().equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private JField findField(JClass owner, String obfuName) {
        if(owner == null) {
            return null;
        }
        for (JField field : owner.getFields()) {
            if (field.getObfuscatedName().equals(obfuName)) {
                return field;
            }
        }
        return null;
    }
}