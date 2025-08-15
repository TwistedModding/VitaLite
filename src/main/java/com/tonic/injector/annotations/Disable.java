package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injector annotation for methods that should be disabled.
 *
 *Method should have a boolean return type. If it returns true, the targeted
 * gamepack method fires as normal, if it returns false, the targeted gamepack
 * method returns immediately without executing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Disable {
    String value();
}
