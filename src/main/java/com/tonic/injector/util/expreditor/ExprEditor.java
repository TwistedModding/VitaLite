package com.tonic.injector.util.expreditor;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * ASM-based expression editor inspired by Javassist's ExprEditor.
 * Allows editing field accesses, method calls, array accesses, and literal values within method bodies.
 */
public abstract class ExprEditor {
    
    /**
     * Edit a field access (GETFIELD/PUTFIELD/GETSTATIC/PUTSTATIC).
     * Override this method to modify field access expressions.
     * 
     * @param access the field access expression
     */
    public void edit(FieldAccess access) {
        // Default: do nothing
    }
    
    /**
     * Edit a method call (INVOKEVIRTUAL/INVOKESPECIAL/INVOKESTATIC/INVOKEINTERFACE).
     * Override this method to modify method call expressions.
     * 
     * @param call the method call expression
     */
    public void edit(MethodCall call) {
        // Default: do nothing
    }
    
    /**
     * Edit an array access (AALOAD/AASTORE/IALOAD/IASTORE/etc.).
     * Override this method to modify array access expressions.
     * 
     * @param access the array access expression
     */
    public void edit(ArrayAccess access) {
        // Default: do nothing
    }
    
    /**
     * Edit a literal value (constants, LDC values, etc.).
     * Override this method to modify literal value expressions.
     * 
     * @param literal the literal value expression
     */
    public void edit(LiteralValue literal) {
        // Default: do nothing
    }
    
    /**
     * Process all expressions in the given method.
     * 
     * @param classNode the class containing the method
     * @param method the method to process
     */
    public final void instrument(ClassNode classNode, MethodNode method) {
        ExpressionProcessor processor = new ExpressionProcessor(this, classNode, method);
        processor.process();
    }
    
    /**
     * Process all expressions in all methods of the given class.
     * 
     * @param classNode the class to process
     */
    public final void instrument(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.instructions != null) {
                instrument(classNode, method);
            }
        }
    }
}