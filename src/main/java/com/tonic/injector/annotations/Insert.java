package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects method calls after specified instruction patterns.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Insert {
    /**
     * Target method name to inject into.
     * @return method name
     */
    String method();

    String desc() default "";
    
    /**
     * Instruction pattern to match for injection.
     * @return pattern specification
     */
    At at();
    
    /**
     * Whether to inject after all matches or just first.
     * @return true for all matches, false for first only
     */
    boolean all() default false;
    
    /**
     * Which match to target when all=false (0-based index).
     * @return match index, -1 for last match
     */
    int ordinal() default 0;
    
    /**
     * Optional slice to limit search area.
     * @return slice specification
     */
    Slice slice() default @Slice;

    /**
     * Whether to use raw mode for direct instruction manipulation.
     * @return true for raw mode
     */
    boolean raw() default false;
}