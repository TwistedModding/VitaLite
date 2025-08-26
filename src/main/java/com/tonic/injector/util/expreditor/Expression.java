package com.tonic.injector.util.expreditor;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/**
 * Base class for expressions that can be edited.
 */
public abstract class Expression {
    protected final ClassNode classNode;
    protected final MethodNode method;
    protected final AbstractInsnNode instruction;
    protected final int index;
    
    public Expression(ClassNode classNode, MethodNode method, AbstractInsnNode instruction, int index) {
        this.classNode = classNode;
        this.method = method;
        this.instruction = instruction;
        this.index = index;
    }
    
    /**
     * Replace this expression with the given instruction list.
     * 
     * @param replacement the new instructions to replace this expression
     */
    public void replace(InsnList replacement) {
        method.instructions.insert(instruction, replacement);
        method.instructions.remove(instruction);
    }
    
    /**
     * Insert instructions before this expression.
     * 
     * @param instructions the instructions to insert
     */
    public void insertBefore(InsnList instructions) {
        method.instructions.insertBefore(instruction, instructions);
    }
    
    /**
     * Insert instructions after this expression.
     * 
     * @param instructions the instructions to insert
     */
    public void insertAfter(InsnList instructions) {
        method.instructions.insert(instruction, instructions);
    }
    
    /**
     * Get the class containing this expression.
     */
    public ClassNode getClassNode() {
        return classNode;
    }
    
    /**
     * Get the method containing this expression.
     */
    public MethodNode getMethod() {
        return method;
    }
    
    /**
     * Get the instruction representing this expression.
     */
    public AbstractInsnNode getInstruction() {
        return instruction;
    }
    
    /**
     * Get the index of this instruction in the method.
     */
    public int getIndex() {
        return index;
    }
}