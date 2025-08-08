package com.tonic.remapper.editor.analasys;

import com.tonic.dto.JClass;
import com.tonic.dto.JField;
import com.tonic.dto.JMethod;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Complete bytecode renaming utility with full hierarchy support and InvokeDynamic handling.
 *
 * Features:
 * - Handles class inheritance hierarchies properly
 * - Ensures method overrides get the same name
 * - Handles field inheritance and cross-class references
 * - Full InvokeDynamic support including bootstrap methods and method handles
 * - Handles ConstantDynamic and all dynamic constant pool entries
 * - Adds @ObfuscatedName annotations to preserve original names
 */
public class BytecodeRenamer {
    private static final int ASM_VERSION = Opcodes.ASM9;
    private static final String DEFAULT_ANNOTATION_DESCRIPTOR = "Lcom/tonic/remapper/ObfuscatedName;";

    private final List<ClassNode> classes;
    private final Map<String, ClassNode> classNodeMap = new HashMap<>();
    private final Map<String, String> classMapping = new HashMap<>();
    private final Map<String, Map<String, String>> fieldMapping = new HashMap<>();
    private final Map<String, Map<String, String>> methodMapping = new HashMap<>();

    // Hierarchy tracking
    private final Map<String, Set<String>> classHierarchy = new HashMap<>();
    private final Map<String, Set<String>> subclasses = new HashMap<>();
    private final Map<String, Set<String>> implementers = new HashMap<>();
    private final Map<MethodSignature, String> globalMethodNames = new HashMap<>();
    private final Map<FieldSignature, String> globalFieldNames = new HashMap<>();

    private final List<JClass> mappings;
    private final HierarchyAwareRemapper remapper;
    private final String annotationDescriptor;

    private int classCounter = 0;
    private int fieldCounter = 0;
    private int methodCounter = 0;

    public BytecodeRenamer(List<ClassNode> classes, List<JClass> mappings) {
        this(classes, mappings, DEFAULT_ANNOTATION_DESCRIPTOR);
    }

    public BytecodeRenamer(List<ClassNode> classes, List<JClass> mappings, String annotationDescriptor) {
        this.classes = classes;
        this.annotationDescriptor = annotationDescriptor;
        this.mappings = mappings;

        // Build class node map
        for (ClassNode cn : classes) {
            classNodeMap.put(cn.name, cn);
        }

        // Build hierarchy before creating remapper
        buildClassHierarchy();
        this.remapper = new HierarchyAwareRemapper();
    }

    /**
     * Build complete class hierarchy information
     */
    private void buildClassHierarchy() {
        System.out.println("Building class hierarchy...");

        // First pass: build direct relationships
        for (ClassNode cn : classes) {
            Set<String> hierarchy = new HashSet<>();

            // Add superclass
            if (cn.superName != null && !cn.superName.equals("java/lang/Object")) {
                hierarchy.add(cn.superName);
                subclasses.computeIfAbsent(cn.superName, k -> new HashSet<>()).add(cn.name);
            }

            // Add interfaces
            if (cn.interfaces != null) {
                for (String iface : cn.interfaces) {
                    hierarchy.add(iface);
                    implementers.computeIfAbsent(iface, k -> new HashSet<>()).add(cn.name);
                }
            }

            classHierarchy.put(cn.name, hierarchy);
        }

        // Second pass: compute transitive closure
        for (ClassNode cn : classes) {
            Set<String> allAncestors = new HashSet<>();
            Queue<String> toProcess = new LinkedList<>(classHierarchy.get(cn.name));

            while (!toProcess.isEmpty()) {
                String ancestor = toProcess.poll();
                if (allAncestors.add(ancestor)) {
                    Set<String> ancestorHierarchy = classHierarchy.get(ancestor);
                    if (ancestorHierarchy != null) {
                        toProcess.addAll(ancestorHierarchy);
                    }

                    ClassNode ancestorNode = classNodeMap.get(ancestor);
                    if (ancestorNode != null) {
                        if (ancestorNode.superName != null && !ancestorNode.superName.equals("java/lang/Object")) {
                            toProcess.add(ancestorNode.superName);
                        }
                        if (ancestorNode.interfaces != null) {
                            toProcess.addAll(ancestorNode.interfaces);
                        }
                    }
                }
            }

            classHierarchy.put(cn.name, allAncestors);
        }

        System.out.println("Hierarchy built for " + classHierarchy.size() + " classes");
    }

    /**
     * Build mappings with hierarchy awareness
     */
    private void buildMappings() {
        System.out.println("Building mappings with hierarchy awareness...");

        // First: map classes
        for (ClassNode cn : classes) {
            JClass owner = findClass(cn.name);
            if (cn.name.length() <= 2) {
                String newClassName = (owner != null && owner.getName() != null && !owner.getName().isBlank())
                        ? owner.getName()
                        : "class" + (classCounter++);
                classMapping.put(cn.name, newClassName);
            }
        }

        // Second: collect all methods and fields with their inheritance chains
        Map<MethodSignature, Set<String>> methodOwners = new HashMap<>();
        Map<FieldSignature, Set<String>> fieldOwners = new HashMap<>();

        for (ClassNode cn : classes) {
            if(cn.name.contains("/") )
                continue;
            // Collect methods
            for (MethodNode mn : cn.methods) {
                if(mn.name.length() > 2)
                    continue;
                MethodSignature sig = new MethodSignature(mn.name, mn.desc);
                methodOwners.computeIfAbsent(sig, k -> new HashSet<>()).add(cn.name);
            }

            // Collect fields
            for (FieldNode fn : cn.fields) {
                FieldSignature sig = new FieldSignature(fn.name, fn.desc);
                fieldOwners.computeIfAbsent(sig, k -> new HashSet<>()).add(cn.name);
            }
        }

        // Third: assign names to method groups
        for (Map.Entry<MethodSignature, Set<String>> entry : methodOwners.entrySet()) {
            MethodSignature sig = entry.getKey();
            Set<String> owners = entry.getValue();

            if (sig.name.equals("main")) {
                continue;
            }

            Set<String> relatedClasses = findRelatedClasses(owners);

            String assignedName = null;
            for (String className : relatedClasses) {
                JClass jClass = findClass(className);
                if (jClass != null) {
                    JMethod jMethod = findMethod(jClass, sig.name, sig.descriptor);
                    if (jMethod != null && jMethod.getName() != null && !jMethod.getName().isBlank()) {
                        assignedName = jMethod.getName();
                        break;
                    }
                }
            }

            if (assignedName == null) {
                assignedName = "method" + (methodCounter++);
            }

            globalMethodNames.put(sig, assignedName);
            for (String className : relatedClasses) {
                methodMapping.computeIfAbsent(className, k -> new HashMap<>())
                        .put(sig.name + sig.descriptor, assignedName);
            }
        }

        // Fourth: assign names to fields
        for (Map.Entry<FieldSignature, Set<String>> entry : fieldOwners.entrySet()) {
            FieldSignature sig = entry.getKey();
            Set<String> owners = entry.getValue();

            Set<String> relatedClasses = findRelatedClasses(owners);

            String assignedName = null;
            for (String className : relatedClasses) {
                JClass jClass = findClass(className);
                if (jClass != null) {
                    JField jField = findField(jClass, sig.name);
                    if (jField != null && jField.getName() != null && !jField.getName().isBlank()) {
                        assignedName = jField.getName();
                        break;
                    }
                }
            }

            if (assignedName == null) {
                assignedName = "field" + (fieldCounter++);
            }

            globalFieldNames.put(sig, assignedName);
            for (String className : relatedClasses) {
                fieldMapping.computeIfAbsent(className, k -> new HashMap<>())
                        .put(sig.name, assignedName);
            }
        }

        System.out.println("Mappings complete: " + methodCounter + " methods, " + fieldCounter + " fields");
    }

    /**
     * Find all classes related by inheritance
     */
    private Set<String> findRelatedClasses(Set<String> classes) {
        Set<String> related = new HashSet<>(classes);
        Set<String> toProcess = new HashSet<>(classes);

        while (!toProcess.isEmpty()) {
            Set<String> nextRound = new HashSet<>();

            for (String className : toProcess) {
                // Add superclasses and interfaces
                Set<String> ancestors = classHierarchy.get(className);
                if (ancestors != null) {
                    for (String ancestor : ancestors) {
                        if (related.add(ancestor)) {
                            nextRound.add(ancestor);
                        }
                    }
                }

                // Add subclasses
                Set<String> subs = subclasses.get(className);
                if (subs != null) {
                    for (String sub : subs) {
                        if (related.add(sub)) {
                            nextRound.add(sub);
                        }
                    }
                }

                // Add implementers
                Set<String> impls = implementers.get(className);
                if (impls != null) {
                    for (String impl : impls) {
                        if (related.add(impl)) {
                            nextRound.add(impl);
                        }
                    }
                }
            }

            toProcess = nextRound;
        }

        return related;
    }

    public List<ClassNode> rename() {
        return rename(false);
    }

    public List<ClassNode> rename(boolean verbose) {
        buildMappings();
        List<ClassNode> renamedClasses = new ArrayList<>();

        for (ClassNode cn : classes) {
            ClassNode renamed = applyRenaming(cn);

            if (verbose) {
                System.out.println("Class " + cn.name + " -> " + renamed.name);
                verifyClassNode(renamed);
            }

            renamedClasses.add(renamed);
        }

        System.out.println("Renamed " + renamedClasses.size() + " classes");
        return renamedClasses;
    }

    private ClassNode applyRenaming(ClassNode original) {
        ClassNode renamed = new ClassNode(ASM_VERSION);

        // Use enhanced remapper
        EnhancedClassRemapper classRemapper = new EnhancedClassRemapper(renamed, this.remapper);
        original.accept(classRemapper);

        // Post-process InvokeDynamic instructions
        postProcessInvokeDynamic(renamed);

        // Add annotations
        if (classMapping.containsKey(original.name)) {
            addClassAnnotation(renamed, original.name);
        }
        addFieldAndMethodAnnotations(renamed, original);

        return renamed;
    }

    /**
     * Post-process all methods to handle InvokeDynamic instructions
     */
    private void postProcessInvokeDynamic(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) continue;

            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;

                    // Remap the name
                    String newName = remapper.mapInvokeDynamicMethodName(indy.name, indy.desc);
                    if (!newName.equals(indy.name)) {
                        indy.name = newName;
                    }

                    // Remap the descriptor
                    indy.desc = remapper.mapMethodDesc(indy.desc);

                    // Process bootstrap method
                    if (indy.bsm != null) {
                        indy.bsm = remapHandle(indy.bsm);
                    }

                    // Process bootstrap method arguments
                    if (indy.bsmArgs != null) {
                        for (int i = 0; i < indy.bsmArgs.length; i++) {
                            indy.bsmArgs[i] = remapBootstrapArgument(indy.bsmArgs[i]);
                        }
                    }
                }
                // Handle LDC instructions with method handles
                else if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    ldc.cst = remapConstant(ldc.cst);
                }
            }
        }
    }

    /**
     * Remap a bootstrap method argument
     */
    private Object remapBootstrapArgument(Object arg) {
        if (arg instanceof Type) {
            Type type = (Type) arg;
            if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                return Type.getType(remapper.mapDesc(type.getDescriptor()));
            }
            return type;
        } else if (arg instanceof Handle) {
            return remapHandle((Handle) arg);
        } else if (arg instanceof ConstantDynamic) {
            return remapConstantDynamic((ConstantDynamic) arg);
        } else if (arg instanceof String) {
            // Sometimes class names are passed as strings
            String str = (String) arg;
            if (str.contains("/") && !str.contains(" ")) {
                String mapped = classMapping.get(str);
                if (mapped != null) {
                    return mapped;
                }
            }
            return str;
        }
        return arg;
    }

    /**
     * Remap a method handle
     */
    private Handle remapHandle(Handle handle) {
        String owner = remapper.map(handle.getOwner());
        String name = handle.getName();
        String desc = handle.getDesc();

        switch (handle.getTag()) {
            case Opcodes.H_GETFIELD:
            case Opcodes.H_GETSTATIC:
            case Opcodes.H_PUTFIELD:
            case Opcodes.H_PUTSTATIC:
                // Field handle
                name = remapper.mapFieldName(handle.getOwner(), handle.getName(), desc);
                desc = remapper.mapDesc(desc);
                break;

            case Opcodes.H_INVOKEVIRTUAL:
            case Opcodes.H_INVOKESTATIC:
            case Opcodes.H_INVOKESPECIAL:
            case Opcodes.H_NEWINVOKESPECIAL:
            case Opcodes.H_INVOKEINTERFACE:
                // Method handle
                name = remapper.mapMethodName(handle.getOwner(), handle.getName(), desc);
                desc = remapper.mapMethodDesc(desc);
                break;
        }

        if (owner.equals(handle.getOwner()) && name.equals(handle.getName()) && desc.equals(handle.getDesc())) {
            return handle;
        }

        return new Handle(handle.getTag(), owner, name, desc, handle.isInterface());
    }

    /**
     * Remap a ConstantDynamic
     */
    private ConstantDynamic remapConstantDynamic(ConstantDynamic condy) {
        String name = condy.getName();
        String desc = remapper.mapDesc(condy.getDescriptor());
        Handle bsm = remapHandle(condy.getBootstrapMethod());

        // Remap bootstrap method arguments
        Object[] bsmArgs = new Object[condy.getBootstrapMethodArgumentCount()];
        for (int i = 0; i < bsmArgs.length; i++) {
            bsmArgs[i] = remapBootstrapArgument(condy.getBootstrapMethodArgument(i));
        }

        return new ConstantDynamic(name, desc, bsm, bsmArgs);
    }

    /**
     * Remap constants
     */
    private Object remapConstant(Object cst) {
        if (cst instanceof Type) {
            Type type = (Type) cst;
            String desc = remapper.mapDesc(type.getDescriptor());
            return Type.getType(desc);
        } else if (cst instanceof Handle) {
            return remapHandle((Handle) cst);
        } else if (cst instanceof ConstantDynamic) {
            return remapConstantDynamic((ConstantDynamic) cst);
        }
        return cst;
    }

    /**
     * Enhanced ClassRemapper that handles InvokeDynamic
     */
    private class EnhancedClassRemapper extends ClassRemapper {
        public EnhancedClassRemapper(ClassVisitor classVisitor, Remapper remapper) {
            super(classVisitor, remapper);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new EnhancedMethodRemapper(mv, remapper);
        }
    }

    /**
     * Enhanced MethodRemapper for InvokeDynamic support
     */
    private class EnhancedMethodRemapper extends MethodVisitor {
        private final Remapper remapper;

        public EnhancedMethodRemapper(MethodVisitor methodVisitor, Remapper remapper) {
            super(ASM_VERSION, methodVisitor);
            this.remapper = remapper;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            String remappedName = remapper.mapInvokeDynamicMethodName(name, descriptor);
            String remappedDesc = remapper.mapMethodDesc(descriptor);
            Handle remappedBsm = remapHandle(bootstrapMethodHandle);

            Object[] remappedArgs = new Object[bootstrapMethodArguments.length];
            for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                remappedArgs[i] = remapBootstrapArgument(bootstrapMethodArguments[i]);
            }

            super.visitInvokeDynamicInsn(remappedName, remappedDesc, remappedBsm, remappedArgs);
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(remapConstant(value));
        }
    }

    /**
     * Hierarchy-aware remapper that looks up the inheritance chain
     */
    private class HierarchyAwareRemapper extends Remapper {
        @Override
        public String map(String internalName) {
            return classMapping.getOrDefault(internalName, internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            // First try direct owner
            Map<String, String> fieldMap = fieldMapping.get(owner);
            if (fieldMap != null) {
                String mapped = fieldMap.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }

            // Try global field names
            FieldSignature sig = new FieldSignature(name, descriptor);
            String globalName = globalFieldNames.get(sig);
            if (globalName != null) {
                return globalName;
            }

            // Check superclasses
            Set<String> ancestors = classHierarchy.get(owner);
            if (ancestors != null) {
                for (String ancestor : ancestors) {
                    fieldMap = fieldMapping.get(ancestor);
                    if (fieldMap != null) {
                        String mapped = fieldMap.get(name);
                        if (mapped != null) {
                            fieldMapping.computeIfAbsent(owner, k -> new HashMap<>()).put(name, mapped);
                            return mapped;
                        }
                    }
                }
            }

            return name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.equals("<init>") || name.equals("<clinit>") || name.equals("main")) {
                return name;
            }

            // First try direct owner
            Map<String, String> methodMap = methodMapping.get(owner);
            if (methodMap != null) {
                String mapped = methodMap.get(name + descriptor);
                if (mapped != null) {
                    return mapped;
                }
            }

            // Try global method names
            MethodSignature sig = new MethodSignature(name, descriptor);
            String globalName = globalMethodNames.get(sig);
            if (globalName != null) {
                return globalName;
            }

            // Check superclasses
            Set<String> ancestors = classHierarchy.get(owner);
            if (ancestors != null) {
                for (String ancestor : ancestors) {
                    methodMap = methodMapping.get(ancestor);
                    if (methodMap != null) {
                        String mapped = methodMap.get(name + descriptor);
                        if (mapped != null) {
                            methodMapping.computeIfAbsent(owner, k -> new HashMap<>())
                                    .put(name + descriptor, mapped);
                            return mapped;
                        }
                    }
                }
            }

            return name;
        }

        @Override
        public String mapInvokeDynamicMethodName(String name, String descriptor) {
            // Check global method names first
            MethodSignature sig = new MethodSignature(name, descriptor);
            String globalName = globalMethodNames.get(sig);
            if (globalName != null) {
                return globalName;
            }

            // Check all classes
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

    /**
     * Method signature for tracking overrides
     */
    private static class MethodSignature {
        final String name;
        final String descriptor;

        MethodSignature(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodSignature)) return false;
            MethodSignature that = (MethodSignature) o;
            return name.equals(that.name) && descriptor.equals(that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, descriptor);
        }
    }

    /**
     * Field signature for tracking inheritance
     */
    private static class FieldSignature {
        final String name;
        final String descriptor;

        FieldSignature(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldSignature)) return false;
            FieldSignature that = (FieldSignature) o;
            return name.equals(that.name) && descriptor.equals(that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, descriptor);
        }
    }

    // Helper methods
    private void verifyClassNode(ClassNode cn) {
        try {
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            byte[] bytes = cw.toByteArray();
            System.out.println("  Verification: Class " + cn.name + " produces " + bytes.length + " bytes");
        } catch (Exception e) {
            System.err.println("  ERROR: Failed to verify class " + cn.name + ": " + e.getMessage());
        }
    }

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

    public Map<String, Object> getMappings() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("classes", new HashMap<>(classMapping));
        mappings.put("fields", new HashMap<>(fieldMapping));
        mappings.put("methods", new HashMap<>(methodMapping));
        return mappings;
    }

    // Finder methods
    private JClass findClass(String obfuName) {
        if (mappings == null) return null;
        for (JClass jClass : mappings) {
            if (jClass.getObfuscatedName().equals(obfuName)) {
                return jClass;
            }
        }
        return null;
    }

    private JMethod findMethod(JClass owner, String obfuName, String desc) {
        if (owner == null) return null;
        for (JMethod method : owner.getMethods()) {
            if (method.getObfuscatedName().equals(obfuName) && method.getDescriptor().equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private JField findField(JClass owner, String obfuName) {
        if (owner == null) return null;
        for (JField field : owner.getFields()) {
            if (field.getObfuscatedName().equals(obfuName)) {
                return field;
            }
        }
        return null;
    }
}