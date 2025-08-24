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
     * The type of instruction pattern to match using enum (recommended).
     * Use this for better IDE support and compile-time validation.
     * 
     * Example: @At(AtTarget.PUTFIELD)
     */
    AtTarget value() default AtTarget.INVOKE;
    
    /**
     * Legacy string-based pattern type (for backwards compatibility).
     * Use the enum 'value' parameter instead for new code.
     * 
     * @deprecated Use {@link #value()} with AtTarget enum instead
     */
    @Deprecated
    String stringValue() default "";
    
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
     * Whether to match the instruction before the target (HEAD) or after (TAIL) using enum (recommended).
     * Use this for better IDE support and compile-time validation.
     * Default: Shift.TAIL (after the instruction)
     * 
     * Example: @At(value = AtTarget.INVOKE, target = "method", shift = Shift.HEAD)
     */
    Shift shift() default Shift.TAIL;
    
    /**
     * Legacy string-based shift specification (for backwards compatibility).
     * Use the enum 'shift' parameter instead for new code.
     * 
     * @deprecated Use {@link #shift()} with Shift enum instead
     */
    @Deprecated
    String shiftString() default "";
}