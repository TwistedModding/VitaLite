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
}