package com.tonic.injector.util.expreditor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Internal processor that walks through method instructions and identifies expressions.
 */
class ExpressionProcessor {
    private final ExprEditor editor;
    private final ClassNode classNode;
    private final MethodNode method;
    
    public ExpressionProcessor(ExprEditor editor, ClassNode classNode, MethodNode method) {
        this.editor = editor;
        this.classNode = classNode;
        this.method = method;
    }
    
    public void process() {
        if (method.instructions == null) {
            return;
        }
        
        // Create a copy of the instruction array to avoid ConcurrentModificationException
        // when instructions are modified during processing
        AbstractInsnNode[] instructions = method.instructions.toArray();
        
        for (int i = 0; i < instructions.length; i++) {
            AbstractInsnNode insn = instructions[i];
            
            if (insn instanceof FieldInsnNode) {
                processFieldAccess((FieldInsnNode) insn, i);
            } else if (insn instanceof MethodInsnNode) {
                processMethodCall((MethodInsnNode) insn, i);
            } else if (insn instanceof InsnNode) {
                processArrayAccess((InsnNode) insn, i);
                processLiteralValue(insn, i);
            } else if (insn instanceof IntInsnNode || insn instanceof LdcInsnNode) {
                processLiteralValue(insn, i);
            } else if (insn instanceof TypeInsnNode) {
                processNewInstance((TypeInsnNode) insn, i);
            }
        }
    }
    
    private void processFieldAccess(FieldInsnNode fieldInsn, int index) {
        if (isFieldAccessOpcode(fieldInsn.getOpcode())) {
            FieldAccess access = new FieldAccess(classNode, method, fieldInsn, index);
            editor.edit(access);
        }
    }
    
    private void processMethodCall(MethodInsnNode methodInsn, int index) {
        if (isMethodCallOpcode(methodInsn.getOpcode())) {
            MethodCall call = new MethodCall(classNode, method, methodInsn, index);
            editor.edit(call);
        }
    }
    
    private void processArrayAccess(InsnNode insnNode, int index) {
        if (isArrayAccessOpcode(insnNode.getOpcode())) {
            ArrayAccess access = new ArrayAccess(classNode, method, insnNode, index);
            editor.edit(access);
        }
    }
    
    private void processLiteralValue(AbstractInsnNode insn, int index) {
        if (isLiteralOpcode(insn.getOpcode())) {
            LiteralValue literal = new LiteralValue(classNode, method, insn, index);
            editor.edit(literal);
        }
    }
    
    private void processNewInstance(TypeInsnNode typeInsn, int index) {
        if (typeInsn.getOpcode() == Opcodes.NEW) {
            NewInstance newInstance = new NewInstance(classNode, method, typeInsn, index);
            editor.edit(newInstance);
        }
    }
    
    private boolean isFieldAccessOpcode(int opcode) {
        return opcode == Opcodes.GETFIELD || 
               opcode == Opcodes.PUTFIELD || 
               opcode == Opcodes.GETSTATIC || 
               opcode == Opcodes.PUTSTATIC;
    }
    
    private boolean isMethodCallOpcode(int opcode) {
        return opcode == Opcodes.INVOKEVIRTUAL || 
               opcode == Opcodes.INVOKESPECIAL || 
               opcode == Opcodes.INVOKESTATIC || 
               opcode == Opcodes.INVOKEINTERFACE;
    }
    
    private boolean isArrayAccessOpcode(int opcode) {
        return opcode == Opcodes.AALOAD || opcode == Opcodes.AASTORE ||
               opcode == Opcodes.BALOAD || opcode == Opcodes.BASTORE ||
               opcode == Opcodes.CALOAD || opcode == Opcodes.CASTORE ||
               opcode == Opcodes.DALOAD || opcode == Opcodes.DASTORE ||
               opcode == Opcodes.FALOAD || opcode == Opcodes.FASTORE ||
               opcode == Opcodes.IALOAD || opcode == Opcodes.IASTORE ||
               opcode == Opcodes.LALOAD || opcode == Opcodes.LASTORE ||
               opcode == Opcodes.SALOAD || opcode == Opcodes.SASTORE;
    }
    
    private boolean isLiteralOpcode(int opcode) {
        // Integer constants
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            return true;
        }
        
        // Other primitive constants
        return opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1 ||
               opcode == Opcodes.FCONST_0 || opcode == Opcodes.FCONST_1 || opcode == Opcodes.FCONST_2 ||
               opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1 ||
               opcode == Opcodes.ACONST_NULL ||
               opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ||
               opcode == Opcodes.LDC;
    }
}