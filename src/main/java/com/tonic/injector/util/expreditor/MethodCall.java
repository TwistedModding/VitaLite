package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Represents a method call expression (INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE).
 */
public class MethodCall extends Expression {
    private final MethodInsnNode methodInsn;
    
    public MethodCall(ClassNode classNode, MethodNode method, MethodInsnNode methodInsn, int index) {
        super(classNode, method, methodInsn, index);
        this.methodInsn = methodInsn;
    }
    
    /**
     * Get the method instruction node.
     */
    public MethodInsnNode getMethodInstruction() {
        return methodInsn;
    }
    
    /**
     * Get the owner class of the method.
     */
    public String getMethodOwner() {
        return methodInsn.owner;
    }
    
    /**
     * Get the name of the method.
     */
    public String getMethodName() {
        return methodInsn.name;
    }
    
    /**
     * Get the descriptor of the method.
     */
    public String getMethodDesc() {
        return methodInsn.desc;
    }
    
    /**
     * Check if this is a static method call (INVOKESTATIC).
     */
    public boolean isStatic() {
        return methodInsn.getOpcode() == Opcodes.INVOKESTATIC;
    }
    
    /**
     * Check if this is a virtual method call (INVOKEVIRTUAL).
     */
    public boolean isVirtual() {
        return methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL;
    }
    
    /**
     * Check if this is a special method call (INVOKESPECIAL) - constructors, super calls, private methods.
     */
    public boolean isSpecial() {
        return methodInsn.getOpcode() == Opcodes.INVOKESPECIAL;
    }
    
    /**
     * Check if this is an interface method call (INVOKEINTERFACE).
     */
    public boolean isInterface() {
        return methodInsn.getOpcode() == Opcodes.INVOKEINTERFACE;
    }
    
    /**
     * Check if this is a constructor call.
     */
    public boolean isConstructor() {
        return "<init>".equals(methodInsn.name);
    }
    
    /**
     * Check if this is a super method call.
     */
    public boolean isSuper() {
        return isSpecial() && !isConstructor() && methodInsn.owner.equals(classNode.superName);
    }
    
    /**
     * Get the opcode of this method call.
     */
    public int getOpcode() {
        return methodInsn.getOpcode();
    }
    
    /**
     * Get the parameter types of this method.
     */
    public Type[] getParameterTypes() {
        return Type.getArgumentTypes(methodInsn.desc);
    }
    
    /**
     * Get the return type of this method.
     */
    public Type getReturnType() {
        return Type.getReturnType(methodInsn.desc);
    }
    
    /**
     * Get the number of parameters this method takes.
     */
    public int getParameterCount() {
        return getParameterTypes().length;
    }
    
    /**
     * Check if this method returns void.
     */
    public boolean returnsVoid() {
        return getReturnType().equals(Type.VOID_TYPE);
    }
    
    /**
     * Get a string representation of the method call type.
     */
    public String getCallType() {
        switch (methodInsn.getOpcode()) {
            case Opcodes.INVOKEVIRTUAL: return "INVOKEVIRTUAL";
            case Opcodes.INVOKESPECIAL: return "INVOKESPECIAL";
            case Opcodes.INVOKESTATIC: return "INVOKESTATIC";
            case Opcodes.INVOKEINTERFACE: return "INVOKEINTERFACE";
            default: return "UNKNOWN";
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s %s.%s%s", 
            getCallType(), 
            getMethodOwner().replace('/', '.'), 
            getMethodName(), 
            getMethodDesc());
    }
}