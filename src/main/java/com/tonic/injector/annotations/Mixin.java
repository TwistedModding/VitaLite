package com.tonic.injector.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Injector annotation for mixin classes.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Mixin {
    String value();
}