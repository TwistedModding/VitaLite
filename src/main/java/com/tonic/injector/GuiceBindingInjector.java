package com.tonic.injector;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class GuiceBindingInjector implements Opcodes {

    // Binding configuration - easily extendable
    private static final List<BindingConfig> BINDINGS = new ArrayList<>();

    public static void patch(ClassNode classNode) {
        if (!classNode.name.equals("net/runelite/client/RuneLiteModule")) {
            return;
        }

        System.out.println("Injecting Guice bindings into RuneLiteModule");

        for (BindingConfig binding : BINDINGS) {
            injectProviderMethod(classNode, binding);
        }
    }

    private static void injectProviderMethod(ClassNode classNode, BindingConfig binding) {
        // Create the provider method
        // Method signature: public TInterface provideTInterface(Client client)
        MethodNode method = new MethodNode(
                ACC_PUBLIC,
                binding.methodName,
                "(Lnet/runelite/api/Client;)L" + binding.returnType + ";",
                null,
                null
        );

        // Add @Provides annotation
        AnnotationNode providesAnnotation = new AnnotationNode("Lcom/google/inject/Provides;");
        if (method.visibleAnnotations == null) {
            method.visibleAnnotations = new ArrayList<>();
        }
        method.visibleAnnotations.add(providesAnnotation);

        // Add @Singleton annotation
        AnnotationNode singletonAnnotation = new AnnotationNode("Ljavax/inject/Singleton;");
        method.visibleAnnotations.add(singletonAnnotation);

        // Add method body
        method.instructions = binding.instructionProvider.createInstructions();

        // Add max stack and locals
        method.maxStack = 2;  // Typically need 2 for these operations
        method.maxLocals = 2; // this + Client parameter

        // Add to class
        classNode.methods.add(method);

        System.out.println("  - Added provider method: " + binding.methodName + " for " + binding.returnType);
    }

    /**
     * Configuration for a binding to be injected
     */
    private static class BindingConfig {
        final String returnType;           // Internal name (e.g., "com/tonic/api/TClient")
        final String methodName;            // Provider method name
        final InstructionProvider instructionProvider;

        BindingConfig(String returnType, String methodName, InstructionProvider provider) {
            this.returnType = returnType;
            this.methodName = methodName;
            this.instructionProvider = provider;
        }
    }

    /**
     * Functional interface for creating method instructions
     */
    @FunctionalInterface
    private interface InstructionProvider {
        InsnList createInstructions();
    }

    /**
     * Helper method to add new bindings programmatically if needed
     */
    public static void addBinding(String returnType, String methodName, InstructionProvider provider) {
        BINDINGS.add(new BindingConfig(returnType, methodName, provider));
    }

    /**
     * Helper to create a simple cast binding
     */
    public static void addCastBinding(String interfaceType, String methodName) {
        addBinding(interfaceType, methodName, () -> {
            InsnList insns = new InsnList();
            insns.add(new VarInsnNode(ALOAD, 1)); // Load Client parameter at index 1
            insns.add(new TypeInsnNode(CHECKCAST, interfaceType));
            insns.add(new InsnNode(ARETURN));
            return insns;
        });
    }

    /**
     * Helper to create a getter binding
     */
    public static void addGetterBinding(String returnType, String methodName,
                                        String castTo, String getterName, String getterDesc) {
        addBinding(returnType, methodName, () -> {
            InsnList insns = new InsnList();
            insns.add(new VarInsnNode(ALOAD, 1)); // Load Client parameter at index 1
            insns.add(new TypeInsnNode(CHECKCAST, castTo));
            insns.add(new MethodInsnNode(
                    INVOKEINTERFACE,
                    castTo,
                    getterName,
                    getterDesc,
                    true
            ));
            insns.add(new InsnNode(ARETURN));
            return insns;
        });
    }
}