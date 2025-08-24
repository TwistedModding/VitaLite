package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a method call after a specified instruction pattern in the target method.
 * 
 * Usage examples:
 * 
 * // Insert after method call
 * @Insert(method = "processPlayer", at = @At(value = "INVOKE", 
 *         target = "updatePosition(FF)V"))
 * 
 * // Insert after field access
 * @Insert(method = "handleMovement", at = @At(value = "GETFIELD", 
 *         target = "player.x"))
 * 
 * // Insert after specific opcode
 * @Insert(method = "calculate", at = @At(value = "OPCODE", 
 *         opcode = "IMUL"))
 * 
 * // Insert after LDC instruction with specific value
 * @Insert(method = "checkBounds", at = @At(value = "LDC", 
 *         constant = @Constant(intValue = 100)))
 * 
 * // Insert at specific line number (if debug info available)
 * @Insert(method = "process", at = @At(value = "LINE", 
 *         line = 42))
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Insert {
    /**
     * The name of the target method to inject into.
     */
    String method();
    
    /**
     * The instruction pattern to match and inject after.
     */
    At at();
    
    /**
     * Whether to inject after all matching instructions or just the first one.
     * Default: false (inject after first match only)
     */
    boolean all() default false;
    
    /**
     * Optional ordinal to specify which match to target when all=false.
     * 0-based index. Default: 0 (first match)
     */
    int ordinal() default 0;
    
    /**
     * Optional slice to limit the search area within the method.
     */
    Slice slice() default @Slice;
}