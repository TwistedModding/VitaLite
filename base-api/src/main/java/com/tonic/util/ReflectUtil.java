package com.tonic.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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

    public static Object newInstance(Class<?> clazz, Class<?>[] argTypes, Object[] values) throws Exception
    {
        if(argTypes == null || argTypes.length == 0)
        {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
        else
        {
            Constructor<?> constructor = clazz.getDeclaredConstructor(argTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        }
    }

    public static void inspectNonStaticFields(Object obj) {
        Class<?> clazz = obj.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);

            try {
                Object value = field.get(obj);
                System.out.println(field.getName() + " = " + value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}