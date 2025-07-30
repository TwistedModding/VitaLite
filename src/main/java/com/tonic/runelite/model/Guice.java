package com.tonic.runelite.model;

import com.google.inject.Binding;
import com.google.inject.Injector;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class Guice
{
    private final Injector injector;

    public Object getByClassName(String fqcn) {
        for (Binding<?> binding : injector.getBindings().values()) {
            Class<?> boundType = binding.getKey()
                    .getTypeLiteral()
                    .getRawType();
            if (boundType.getName().equals(fqcn)) {
                return binding.getProvider().get();
            }
        }
        throw new IllegalArgumentException("No binding for type: " + fqcn);
    }
}
