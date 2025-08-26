package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Represents an array access expression (AALOAD, IALOAD, FALOAD, etc. for reads,
 * AASTORE, IASTORE, FASTORE, etc. for writes).
 */
public class ArrayAccess extends Expression {
    private final InsnNode arrayInsn;
    
    public ArrayAccess(ClassNode classNode, MethodNode method, InsnNode arrayInsn, int index) {
        super(classNode, method, arrayInsn, index);
        this.arrayInsn = arrayInsn;
    }
    
    /**
     * Get the array instruction node.
     */
    public InsnNode getArrayInstruction() {
        return arrayInsn;
    }
    
    /**
     * Check if this is an array read operation (AALOAD, IALOAD, etc.).
     */
    public boolean isReader() {
        int opcode = arrayInsn.getOpcode();
        return opcode == Opcodes.AALOAD || opcode == Opcodes.BALOAD || 
               opcode == Opcodes.CALOAD || opcode == Opcodes.DALOAD || 
               opcode == Opcodes.FALOAD || opcode == Opcodes.IALOAD || 
               opcode == Opcodes.LALOAD || opcode == Opcodes.SALOAD;
    }
    
    /**
     * Check if this is an array write operation (AASTORE, IASTORE, etc.).
     */
    public boolean isWriter() {
        int opcode = arrayInsn.getOpcode();
        return opcode == Opcodes.AASTORE || opcode == Opcodes.BASTORE || 
               opcode == Opcodes.CASTORE || opcode == Opcodes.DASTORE || 
               opcode == Opcodes.FASTORE || opcode == Opcodes.IASTORE || 
               opcode == Opcodes.LASTORE || opcode == Opcodes.SASTORE;
    }
    
    /**
     * Get the opcode of this array access.
     */
    public int getOpcode() {
        return arrayInsn.getOpcode();
    }
    
    /**
     * Get the element type of the array being accessed.
     */
    public Type getElementType() {
        switch (arrayInsn.getOpcode()) {
            case Opcodes.AALOAD:
            case Opcodes.AASTORE:
                return Type.getType(Object.class);
            case Opcodes.BALOAD:
            case Opcodes.BASTORE:
                return Type.BYTE_TYPE;
            case Opcodes.CALOAD:
            case Opcodes.CASTORE:
                return Type.CHAR_TYPE;
            case Opcodes.DALOAD:
            case Opcodes.DASTORE:
                return Type.DOUBLE_TYPE;
            case Opcodes.FALOAD:
            case Opcodes.FASTORE:
                return Type.FLOAT_TYPE;
            case Opcodes.IALOAD:
            case Opcodes.IASTORE:
                return Type.INT_TYPE;
            case Opcodes.LALOAD:
            case Opcodes.LASTORE:
                return Type.LONG_TYPE;
            case Opcodes.SALOAD:
            case Opcodes.SASTORE:
                return Type.SHORT_TYPE;
            default:
                return Type.getType(Object.class);
        }
    }
    
    /**
     * Get a string representation of the array access type.
     */
    public String getAccessType() {
        switch (arrayInsn.getOpcode()) {
            case Opcodes.AALOAD: return "AALOAD";
            case Opcodes.AASTORE: return "AASTORE";
            case Opcodes.BALOAD: return "BALOAD";
            case Opcodes.BASTORE: return "BASTORE";
            case Opcodes.CALOAD: return "CALOAD";
            case Opcodes.CASTORE: return "CASTORE";
            case Opcodes.DALOAD: return "DALOAD";
            case Opcodes.DASTORE: return "DASTORE";
            case Opcodes.FALOAD: return "FALOAD";
            case Opcodes.FASTORE: return "FASTORE";
            case Opcodes.IALOAD: return "IALOAD";
            case Opcodes.IASTORE: return "IASTORE";
            case Opcodes.LALOAD: return "LALOAD";
            case Opcodes.LASTORE: return "LASTORE";
            case Opcodes.SALOAD: return "SALOAD";
            case Opcodes.SASTORE: return "SASTORE";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * Get the type name of the array element.
     */
    public String getElementTypeName() {
        Type elementType = getElementType();
        if (elementType == Type.getType(Object.class)) {
            return "Object"; // Could be any reference type
        }
        return elementType.getClassName();
    }
    
    /**
     * Check if this array access is for a primitive array.
     */
    public boolean isPrimitiveArray() {
        return getElementType().getSort() != Type.OBJECT;
    }
    
    /**
     * Check if this array access is for a reference array.
     */
    public boolean isReferenceArray() {
        return getElementType().getSort() == Type.OBJECT;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s %s)", 
            getAccessType(), 
            isReader() ? "read" : "write",
            getElementTypeName() + "[]");
    }
}