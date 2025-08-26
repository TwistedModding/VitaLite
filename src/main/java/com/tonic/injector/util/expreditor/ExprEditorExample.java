package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Example usage of the ExprEditor ASM implementation.
 * 
 * Usage examples:
 * 1. Log all field accesses
 * 2. Replace method calls
 * 3. Add logging before/after expressions
 */
public class ExprEditorExample {
    
    /**
     * Example editor that logs all field accesses, method calls, and array accesses.
     */
    public static class LoggingExprEditor extends ExprEditor {
        @Override
        public void edit(FieldAccess access) {
            System.out.printf("Found field access: %s at %s.%s%n", 
                access.toString(),
                access.getClassNode().name.replace('/', '.'),
                access.getMethod().name);
        }
        
        @Override
        public void edit(MethodCall call) {
            System.out.printf("Found method call: %s at %s.%s%n", 
                call.toString(),
                call.getClassNode().name.replace('/', '.'),
                call.getMethod().name);
        }
        
        @Override
        public void edit(ArrayAccess access) {
            System.out.printf("Found array access: %s at %s.%s%n", 
                access.toString(),
                access.getClassNode().name.replace('/', '.'),
                access.getMethod().name);
        }
    }
    
    /**
     * Example editor that replaces System.out.println calls with custom logging.
     */
    public static class SystemOutReplacer extends ExprEditor {
        @Override
        public void edit(MethodCall call) {
            if (call.getMethodOwner().equals("java/io/PrintStream") && 
                call.getMethodName().equals("println") && 
                call.getMethodDesc().equals("(Ljava/lang/String;)V")) {
                
                // Replace System.out.println with custom logger
                InsnList replacement = new InsnList();
                replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/tonic/Logger", // Your custom logger class
                    "info",
                    "(Ljava/lang/String;)V",
                    false
                ));
                
                call.replace(replacement);
                System.out.println("Replaced System.out.println with Logger.info");
            }
        }
    }
    
    /**
     * Example editor that adds null checks before field accesses.
     */
    public static class NullCheckAdder extends ExprEditor {
        @Override
        public void edit(FieldAccess access) {
            if (access.isInstance() && access.isReader()) {
                // Add null check before instance field read
                InsnList nullCheck = new InsnList();
                nullCheck.add(new InsnNode(Opcodes.DUP)); // Duplicate object reference
                nullCheck.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/util/Objects",
                    "requireNonNull",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                ));
                nullCheck.add(new InsnNode(Opcodes.POP)); // Remove the returned object
                
                access.insertBefore(nullCheck);
                System.out.printf("Added null check before field access: %s%n", access);
            }
        }
    }
    
    /**
     * Example editor that adds bounds checking to array accesses.
     */
    public static class ArrayBoundsChecker extends ExprEditor {
        @Override
        public void edit(ArrayAccess access) {
            if (access.isReader()) {
                // Add bounds check before array read
                InsnList boundsCheck = new InsnList();
                // Stack: ..., arrayref, index
                boundsCheck.add(new InsnNode(Opcodes.DUP2)); // ..., arrayref, index, arrayref, index
                boundsCheck.add(new InsnNode(Opcodes.SWAP));  // ..., arrayref, index, index, arrayref
                boundsCheck.add(new InsnNode(Opcodes.ARRAYLENGTH)); // ..., arrayref, index, index, length
                boundsCheck.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/util/Objects",
                    "checkIndex", 
                    "(II)I",
                    false
                )); // ..., arrayref, index, validatedIndex
                boundsCheck.add(new InsnNode(Opcodes.POP)); // ..., arrayref, index
                
                access.insertBefore(boundsCheck);
                System.out.printf("Added bounds check to array read: %s%n", access);
            }
        }
    }
    
    /**
     * Example usage of the expr editors.
     */
    public static void instrumentClass(ClassNode classNode) {
        System.out.println("Instrumenting class: " + classNode.name);
        
        // Apply logging editor
        new LoggingExprEditor().instrument(classNode);
        
        // Apply System.out.println replacer
        new SystemOutReplacer().instrument(classNode);
        
        // Apply null check adder
        new NullCheckAdder().instrument(classNode);
        
        // Apply array bounds checker
        new ArrayBoundsChecker().instrument(classNode);
    }
}