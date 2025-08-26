package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Represents a literal value expression (constants, LDC values, etc.).
 */
public class LiteralValue extends Expression {
    private final AbstractInsnNode literalInsn;
    private final Object value;
    private final LiteralType literalType;
    
    public LiteralValue(ClassNode classNode, MethodNode method, AbstractInsnNode literalInsn, int index) {
        super(classNode, method, literalInsn, index);
        this.literalInsn = literalInsn;
        this.value = extractValue(literalInsn);
        this.literalType = determineLiteralType(literalInsn, value);
    }
    
    /**
     * Get the literal instruction node.
     */
    public AbstractInsnNode getLiteralInstruction() {
        return literalInsn;
    }
    
    /**
     * Get the literal value.
     */
    public Object getValue() {
        return value;
    }
    
    /**
     * Get the type of this literal.
     */
    public LiteralType getLiteralType() {
        return literalType;
    }
    
    /**
     * Get the Java type of this literal.
     */
    public Type getJavaType() {
        switch (literalType) {
            case INTEGER: return Type.INT_TYPE;
            case LONG: return Type.LONG_TYPE;
            case FLOAT: return Type.FLOAT_TYPE;
            case DOUBLE: return Type.DOUBLE_TYPE;
            case STRING: return Type.getType(String.class);
            case NULL: return Type.getType(Object.class);
            case CLASS: return Type.getType(Class.class);
            default: return Type.getType(Object.class);
        }
    }
    
    /**
     * Check if this is a numeric literal.
     */
    public boolean isNumeric() {
        return literalType == LiteralType.INTEGER || literalType == LiteralType.LONG ||
               literalType == LiteralType.FLOAT || literalType == LiteralType.DOUBLE;
    }
    
    /**
     * Check if this is an integer literal (including byte, short, int).
     */
    public boolean isInteger() {
        return literalType == LiteralType.INTEGER;
    }
    
    /**
     * Check if this is a string literal.
     */
    public boolean isString() {
        return literalType == LiteralType.STRING;
    }
    
    /**
     * Check if this is a null literal.
     */
    public boolean isNull() {
        return literalType == LiteralType.NULL;
    }
    
    /**
     * Get the integer value if this is an integer literal.
     */
    public Integer getIntValue() {
        return isInteger() ? (Integer) value : null;
    }
    
    /**
     * Get the long value if this is a long literal.
     */
    public Long getLongValue() {
        return literalType == LiteralType.LONG ? (Long) value : null;
    }
    
    /**
     * Get the float value if this is a float literal.
     */
    public Float getFloatValue() {
        return literalType == LiteralType.FLOAT ? (Float) value : null;
    }
    
    /**
     * Get the double value if this is a double literal.
     */
    public Double getDoubleValue() {
        return literalType == LiteralType.DOUBLE ? (Double) value : null;
    }
    
    /**
     * Get the string value if this is a string literal.
     */
    public String getStringValue() {
        return isString() ? (String) value : null;
    }
    
    /**
     * Get the class value if this is a class literal.
     */
    public Type getClassValue() {
        return literalType == LiteralType.CLASS ? (Type) value : null;
    }
    
    /**
     * Get the opcode of this literal instruction.
     */
    public int getOpcode() {
        return literalInsn.getOpcode();
    }
    
    private Object extractValue(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        
        // Handle ICONST instructions
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            return opcode - Opcodes.ICONST_0; // -1 to 5
        }
        
        // Handle other constant instructions
        switch (opcode) {
            case Opcodes.LCONST_0: return 0L;
            case Opcodes.LCONST_1: return 1L;
            case Opcodes.FCONST_0: return 0.0f;
            case Opcodes.FCONST_1: return 1.0f;
            case Opcodes.FCONST_2: return 2.0f;
            case Opcodes.DCONST_0: return 0.0d;
            case Opcodes.DCONST_1: return 1.0d;
            case Opcodes.ACONST_NULL: return null;
            
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return ((IntInsnNode) insn).operand;
                
            case Opcodes.LDC:
                return ((LdcInsnNode) insn).cst;
                
            default:
                return null;
        }
    }
    
    private LiteralType determineLiteralType(AbstractInsnNode insn, Object value) {
        int opcode = insn.getOpcode();
        
        if (opcode == Opcodes.ACONST_NULL) {
            return LiteralType.NULL;
        }
        
        if (value instanceof Integer) {
            return LiteralType.INTEGER;
        } else if (value instanceof Long) {
            return LiteralType.LONG;
        } else if (value instanceof Float) {
            return LiteralType.FLOAT;
        } else if (value instanceof Double) {
            return LiteralType.DOUBLE;
        } else if (value instanceof String) {
            return LiteralType.STRING;
        } else if (value instanceof Type) {
            return LiteralType.CLASS;
        }
        
        return LiteralType.UNKNOWN;
    }
    
    @Override
    public String toString() {
        if (isNull()) {
            return "null";
        } else if (isString()) {
            return "\"" + value + "\"";
        } else if (literalType == LiteralType.LONG) {
            return value + "L";
        } else if (literalType == LiteralType.FLOAT) {
            return value + "f";
        } else if (literalType == LiteralType.DOUBLE) {
            return value + "d";
        } else if (literalType == LiteralType.CLASS) {
            return ((Type) value).getClassName() + ".class";
        } else {
            return String.valueOf(value);
        }
    }
    
    /**
     * Types of literal values.
     */
    public enum LiteralType {
        INTEGER,    // int, byte, short, boolean (all use ICONST/BIPUSH/SIPUSH)
        LONG,       // long
        FLOAT,      // float
        DOUBLE,     // double
        STRING,     // String
        NULL,       // null reference
        CLASS,      // Class literal
        UNKNOWN     // Unknown type
    }
}