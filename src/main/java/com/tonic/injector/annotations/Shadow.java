package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shadows fields and methods from target classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Shadow {
    /**
     * The name of the field or method to shadow.
     * @return target field or method name
     */
    String value();

    /**
     * Whether the target is a runelite injected method or not (Special pipeline to enable using generics to return
     * types we don't have context of).
     * @return true if the target is from RuneLite, false otherwise
     */
    boolean isRuneLites() default false;
}