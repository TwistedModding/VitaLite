package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a constant value to match in LDC instructions.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Constant {
    /**
     * Integer constant value to match.
     */
    int intValue() default Integer.MIN_VALUE;
    
    /**
     * Long constant value to match.
     */
    long longValue() default Long.MIN_VALUE;
    
    /**
     * Float constant value to match.
     */
    float floatValue() default Float.NEGATIVE_INFINITY;
    
    /**
     * Double constant value to match.
     */
    double doubleValue() default Double.NEGATIVE_INFINITY;
    
    /**
     * String constant value to match.
     */
    String stringValue() default "\u0000UNSET\u0000";
    
    /**
     * Class constant value to match.
     */
    Class<?> classValue() default Void.class;
}