package com.tonic.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectUtil
{
    public static Object getStaticField(Class<?> clazz, String fieldName) throws Exception
    {
        Field injectorField = clazz.getDeclaredField(fieldName);
        injectorField.setAccessible(true);
        return injectorField.get(null);
    }

    public static Object getField(Object object, String fieldName) throws Exception
    {
        Field injectorField = object.getClass().getDeclaredField(fieldName);
        injectorField.setAccessible(true);
        return injectorField.get(object);
    }

    public static Object getStaticMethod(Class<?> clazz, String methodName, Class<?>[] argTypes, Object[] values) throws Exception
    {
        Method method = clazz.getMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(null, values);
    }

    public static Object getMethod(Object object, String methodName, Class<?>[] argTypes, Object[] values) throws Exception
    {
        Method method;
        if(argTypes.length == 0)
        {
            method = object.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(object);
        }

        method = object.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(object, values);
    }
}