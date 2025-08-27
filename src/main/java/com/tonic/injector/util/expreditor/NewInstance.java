package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Represents a new instance creation expression (NEW instruction).
 */
public class NewInstance extends Expression {
    private final TypeInsnNode newInsn;
    private final MethodInsnNode constructorCall;
    
    public NewInstance(ClassNode classNode, MethodNode method, TypeInsnNode newInsn, int index) {
        super(classNode, method, newInsn, index);
        this.newInsn = newInsn;
        this.constructorCall = findConstructorCall(newInsn);
    }
    
    /**
     * Get the NEW instruction node.
     */
    public TypeInsnNode getNewInstruction() {
        return newInsn;
    }
    
    /**
     * Get the constructor call instruction (INVOKESPECIAL <init>) if found.
     */
    public MethodInsnNode getConstructorCall() {
        return constructorCall;
    }
    
    /**
     * Get the class name being instantiated.
     */
    public String getClassName() {
        return newInsn.desc;
    }
    
    /**
     * Get the class name in Java format (with dots instead of slashes).
     */
    public String getJavaClassName() {
        return newInsn.desc.replace('/', '.');
    }
    
    /**
     * Get the Type of the class being instantiated.
     */
    public Type getClassType() {
        return Type.getObjectType(newInsn.desc);
    }
    
    /**
     * Check if this is creating an instance of the specified class.
     */
    public boolean isNewInstance(String className) {
        return newInsn.desc.equals(className.replace('.', '/'));
    }
    
    /**
     * Check if this is creating an instance of the specified class (Java format).
     */
    public boolean isNewInstanceJava(String javaClassName) {
        return getJavaClassName().equals(javaClassName);
    }
    
    /**
     * Get the constructor descriptor if constructor call was found.
     */
    public String getConstructorDescriptor() {
        return constructorCall != null ? constructorCall.desc : null;
    }
    
    /**
     * Get the parameter types of the constructor.
     */
    public Type[] getConstructorParameterTypes() {
        if (constructorCall == null) {
            return new Type[0];
        }
        return Type.getArgumentTypes(constructorCall.desc);
    }
    
    /**
     * Get the number of parameters the constructor takes.
     */
    public int getConstructorParameterCount() {
        return getConstructorParameterTypes().length;
    }
    
    /**
     * Check if this is a no-argument constructor call.
     */
    public boolean isNoArgConstructor() {
        return getConstructorParameterCount() == 0;
    }
    
    /**
     * Check if the constructor call was found (NEW followed by proper <init>).
     */
    public boolean hasConstructorCall() {
        return constructorCall != null;
    }
    
    /**
     * Get the opcode (should always be NEW).
     */
    public int getOpcode() {
        return newInsn.getOpcode();
    }
    
    /**
     * Replace the entire NEW + constructor pattern with custom instructions.
     * This removes both the NEW instruction and the constructor call.
     */
    public void replaceEntirePattern(InsnList replacement) {
        if (constructorCall != null) {
            // Remove constructor call first
            method.instructions.remove(constructorCall);
        }
        
        // Replace the NEW instruction
        replace(replacement);
    }
    
    /**
     * Insert instructions before the NEW instruction.
     */
    @Override
    public void insertBefore(InsnList instructions) {
        method.instructions.insertBefore(newInsn, instructions);
    }
    
    /**
     * Insert instructions after the constructor call (or NEW if no constructor found).
     */
    @Override
    public void insertAfter(InsnList instructions) {
        if (constructorCall != null) {
            method.instructions.insert(constructorCall, instructions);
        } else {
            method.instructions.insert(newInsn, instructions);
        }
    }
    
    private MethodInsnNode findConstructorCall(TypeInsnNode newInsn) {
        AbstractInsnNode current = newInsn.getNext();
        int dupCount = 0;
        
        // Look for the pattern: NEW, DUP, [parameters], INVOKESPECIAL <init>
        while (current != null && dupCount < 5) { // Safety limit
            if (current.getOpcode() == Opcodes.DUP) {
                dupCount++;
            } else if (current instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) current;
                
                // Found constructor call for the same class
                if (methodInsn.getOpcode() == Opcodes.INVOKESPECIAL &&
                    methodInsn.name.equals("<init>") &&
                    methodInsn.owner.equals(newInsn.desc)) {
                    return methodInsn;
                }
                
                // If we find a different method call, stop looking
                break;
            } else if (current.getType() == AbstractInsnNode.LABEL ||
                       current.getType() == AbstractInsnNode.LINE ||
                       current.getType() == AbstractInsnNode.FRAME) {
                // Skip metadata instructions
                current = current.getNext();
                continue;
            }
            
            current = current.getNext();
        }
        
        return null; // Constructor call not found or not matching pattern
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(getJavaClassName()).append("(");
        
        if (constructorCall != null) {
            Type[] paramTypes = getConstructorParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(paramTypes[i].getClassName());
            }
        } else {
            sb.append("?"); // Constructor not found
        }
        
        sb.append(")");
        return sb.toString();
    }
}