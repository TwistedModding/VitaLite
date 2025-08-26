package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Represents a field access expression (GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC).
 */
public class FieldAccess extends Expression {
    private final FieldInsnNode fieldInsn;
    
    public FieldAccess(ClassNode classNode, MethodNode method, FieldInsnNode fieldInsn, int index) {
        super(classNode, method, fieldInsn, index);
        this.fieldInsn = fieldInsn;
    }
    
    /**
     * Get the field instruction node.
     */
    public FieldInsnNode getFieldInstruction() {
        return fieldInsn;
    }
    
    /**
     * Get the owner class of the field.
     */
    public String getFieldOwner() {
        return fieldInsn.owner;
    }
    
    /**
     * Get the name of the field.
     */
    public String getFieldName() {
        return fieldInsn.name;
    }
    
    /**
     * Get the descriptor (type) of the field.
     */
    public String getFieldDesc() {
        return fieldInsn.desc;
    }
    
    /**
     * Check if this is a field read operation (GETFIELD or GETSTATIC).
     */
    public boolean isReader() {
        return fieldInsn.getOpcode() == Opcodes.GETFIELD || fieldInsn.getOpcode() == Opcodes.GETSTATIC;
    }
    
    /**
     * Check if this is a field write operation (PUTFIELD or PUTSTATIC).
     */
    public boolean isWriter() {
        return fieldInsn.getOpcode() == Opcodes.PUTFIELD || fieldInsn.getOpcode() == Opcodes.PUTSTATIC;
    }
    
    /**
     * Check if this is a static field access (GETSTATIC or PUTSTATIC).
     */
    public boolean isStatic() {
        return fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC;
    }
    
    /**
     * Check if this is an instance field access (GETFIELD or PUTFIELD).
     */
    public boolean isInstance() {
        return fieldInsn.getOpcode() == Opcodes.GETFIELD || fieldInsn.getOpcode() == Opcodes.PUTFIELD;
    }
    
    /**
     * Get the opcode of this field access.
     */
    public int getOpcode() {
        return fieldInsn.getOpcode();
    }
    
    /**
     * Get a string representation of the field access type.
     */
    public String getAccessType() {
        switch (fieldInsn.getOpcode()) {
            case Opcodes.GETFIELD: return "GETFIELD";
            case Opcodes.PUTFIELD: return "PUTFIELD";
            case Opcodes.GETSTATIC: return "GETSTATIC";
            case Opcodes.PUTSTATIC: return "PUTSTATIC";
            default: return "UNKNOWN";
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s %s.%s:%s", 
            getAccessType(), 
            getFieldOwner().replace('/', '.'), 
            getFieldName(), 
            getFieldDesc());
    }
}