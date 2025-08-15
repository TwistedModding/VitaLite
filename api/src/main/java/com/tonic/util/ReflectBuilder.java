package com.tonic.util;

import com.tonic.util.reflection.*;
import java.util.ArrayDeque;
import java.util.Deque;

public class ReflectBuilder
{
    private final Deque<Element> reflectionChain = new ArrayDeque<>();
    private final Object start;

    public static ReflectBuilder of(Object start)
    {
        return new ReflectBuilder(start);
    }

    private ReflectBuilder(Object start)
    {
        this.start = start;
    }

    public ReflectBuilder staticField(String name)
    {
        reflectionChain.offer(new FieldElement(true, name));
        return this;
    }

    public ReflectBuilder field(String name)
    {
        reflectionChain.offer(new FieldElement(false, name));
        return this;
    }

    public ReflectBuilder staticMethod(String name, Class<?>[] parameterTypes, Object[] args)
    {
        if (parameterTypes == null)
        {
            parameterTypes = new Class<?>[]{};
        }
        if (args == null)
        {
            args = new Object[]{};
        }
        reflectionChain.offer(new MethodElement(true, name, parameterTypes, args));
        return this;
    }

    public ReflectBuilder method(String name, Class<?>[] parameterTypes, Object[] args)
    {
        if (parameterTypes == null)
        {
            parameterTypes = new Class<?>[]{};
        }
        if (args == null)
        {
            args = new Object[]{};
        }
        reflectionChain.offer(new MethodElement(false, name, parameterTypes, args));
        return this;
    }

    public Object get()
    {
        Object start = this.start;
        try
        {
            while(!reflectionChain.isEmpty())
            {
                Element element = reflectionChain.poll();
                start = element.get(start);
            }
            return start;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to get value from reflection chain", e);
        }
    }
}
