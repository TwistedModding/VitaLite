package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an instruction pattern to match within a method.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface At {
    /**
     * The type of instruction pattern to match.
     */
    AtTarget value() default AtTarget.INVOKE;
    
    /**
     * Target specification for INVOKE, field access, or NEW instructions.
     * 
     * For INVOKE: "methodName(descriptor)" or "methodName"
     * For field access: "fieldName"  
     * For NEW: "className" (or use owner parameter)
     */
    String target() default "";
    
    /**
     * Owner class for the target field/method/type.
     * If not specified, defaults to the mixin target class context.
     * 
     * This gets resolved through mappings first, then falls back to literal name.
     * Examples: "Player", "Entity", "com.example.SomeClass"
     */
    String owner() default "";
    
    /**
     * Specific opcode name when using value="OPCODE".
     * Examples: "IMUL", "IADD", "ASTORE", "ILOAD", etc.
     */
    String opcode() default "";
    
    /**
     * Line number when using value="LINE".
     */
    int line() default -1;
    
    /**
     * Constant value matching for LDC instructions.
     */
    Constant constant() default @Constant;
    
    /**
     * Whether to match the instruction before the target (HEAD) or after (TAIL).
     * Default: Shift.TAIL (after the instruction)
     */
    Shift shift() default Shift.TAIL;
}