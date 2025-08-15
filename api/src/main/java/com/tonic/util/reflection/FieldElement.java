package com.tonic.util.reflection;

import com.tonic.util.ReflectUtil;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FieldElement implements Element
{
    private final boolean isStatic;
    private final String name;

    @Override
    public Object get(Object o) throws Exception {
        if(isStatic)
        {
            return ReflectUtil.getStaticField((Class<?>) o, name);
        }
        else
        {
            return ReflectUtil.getField(o, name);
        }
    }
}
