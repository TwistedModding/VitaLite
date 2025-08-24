package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a slice of a method to limit pattern matching to a specific region.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Slice {
    /**
     * The starting point of the slice.
     */
    At from() default @At(AtTarget.NONE);
    
    /**
     * The ending point of the slice.  
     */
    At to() default @At(AtTarget.NONE);
}