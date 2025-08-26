package com.tonic.injector.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that should be transformed into factory methods for constructing gamepack class instances.
 * The annotated method signature defines the parameters needed for construction, and the transformer
 * creates the appropriate constructor call with proper parameter mapping and type casting.
 *
 * @see com.tonic.injector.pipeline.ConstructTransformer
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Construct {
    String value();
}
