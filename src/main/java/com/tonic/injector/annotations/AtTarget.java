package com.tonic.injector.annotations;

/**
 * Enum defining the types of instruction patterns that can be matched with @At annotations.
 * This provides better IDE support, autocomplete, and compile-time validation compared to string literals.
 */
public enum AtTarget {
    /**
     * Method invocation (INVOKEVIRTUAL, INVOKESTATIC, INVOKEINTERFACE, INVOKESPECIAL)
     * Usage: @At(value = AtTarget.INVOKE, target = "methodName", owner = "ClassName")
     */
    INVOKE,
    
    /**
     * Instance field read access (GETFIELD)
     * Usage: @At(value = AtTarget.GETFIELD, target = "fieldName", owner = "ClassName")
     */
    GETFIELD,
    
    /**
     * Instance field write access (PUTFIELD) 
     * Usage: @At(value = AtTarget.PUTFIELD, target = "fieldName", owner = "ClassName")
     */
    PUTFIELD,
    
    /**
     * Static field read access (GETSTATIC)
     * Usage: @At(value = AtTarget.GETSTATIC, target = "fieldName", owner = "ClassName")
     */
    GETSTATIC,
    
    /**
     * Static field write access (PUTSTATIC)
     * Usage: @At(value = AtTarget.PUTSTATIC, target = "fieldName", owner = "ClassName")
     */
    PUTSTATIC,
    
    /**
     * Object instantiation (NEW)
     * Usage: @At(value = AtTarget.NEW, owner = "ClassName")
     */
    NEW,
    
    /**
     * Load constant instruction (LDC)
     * Usage: @At(value = AtTarget.LDC, constant = @Constant(...))
     */
    LDC,
    
    /**
     * Specific bytecode opcode
     * Usage: @At(value = AtTarget.OPCODE, opcode = "IMUL")
     */
    OPCODE,
    
    /**
     * Line number (requires debug information)
     * Usage: @At(value = AtTarget.LINE, line = 42)
     */
    LINE,
    
    /**
     * Return instructions (RETURN, IRETURN, ARETURN, etc.)
     * Usage: @At(value = AtTarget.RETURN)
     */
    RETURN,
    
    /**
     * Jump instructions (GOTO, IF_*, etc.)
     * Usage: @At(value = AtTarget.JUMP)
     */
    JUMP,
    
    /**
     * Empty/no targeting - used for slice defaults
     * Internal use only
     */
    NONE;
    
    /**
     * Convert enum to string for backwards compatibility with existing string-based logic.
     */
    @Override
    public String toString() {
        return name();
    }
}