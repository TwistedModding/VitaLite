package com.tonic.injector.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationUtil
{
    public static boolean hasAnyAnnotation(MethodNode mn)
    {
        return mn.visibleAnnotations != null && !mn.visibleAnnotations.isEmpty();
    }
    public static boolean hasAnnotation(ClassNode cn, Class<?> annotation)
    {
        if (cn.visibleAnnotations != null) {
            for (AnnotationNode an : cn.visibleAnnotations) {
                if (an.desc.equals(Type.getDescriptor(annotation))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasAnnotation(MethodNode cn, Class<?> annotation)
    {
        if (cn.visibleAnnotations != null) {
            for (AnnotationNode an : cn.visibleAnnotations) {
                if (an.desc.equals(Type.getDescriptor(annotation))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasAnnotation(FieldNode cn, Class<?> annotation)
    {
        if (cn.visibleAnnotations != null) {
            for (AnnotationNode an : cn.visibleAnnotations) {
                if (an.desc.equals(Type.getDescriptor(annotation))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static <T> T getAnnotation(ClassNode cn, Class<?> annotation, String value)
    {
        if (cn.visibleAnnotations != null) {
            for (AnnotationNode an : cn.visibleAnnotations) {
                if (an.desc.equals(Type.getDescriptor(annotation))) {
                    Map<String, Object> params = AnnotationUtil.parseAnnotationValues(an);
                    return (T) params.get(value);
                }
            }
        }
        return (T)null;
    }

    public static <T> T getAnnotation(MethodNode cn, Class<?> annotation, String value)
    {
        if (cn.visibleAnnotations != null) {
            for (AnnotationNode an : cn.visibleAnnotations) {
                if (an.desc.equals(Type.getDescriptor(annotation))) {
                    Map<String, Object> params = AnnotationUtil.parseAnnotationValues(an);
                    return (T) params.get(value);
                }
            }
        }
        return (T)null;
    }

    public static <T> T getAnnotation(FieldNode cn, Class<?> annotation, String value)
    {
        if (cn.visibleAnnotations != null) {
            for (AnnotationNode an : cn.visibleAnnotations) {
                if (an.desc.equals(Type.getDescriptor(annotation))) {
                    Map<String, Object> params = AnnotationUtil.parseAnnotationValues(an);
                    return (T) params.get(value);
                }
            }
        }
        return (T)null;
    }

    /**
     * Parses annotation values into a map.
     */
    public static Map<String, Object> parseAnnotationValues(AnnotationNode annotation) {
        Map<String, Object> params = new HashMap<>();

        if (annotation.values != null) {
            // Annotation values are stored as pairs: [name, value, name, value, ...]
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String name = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                params.put(name, parseValue(value));
            }
        }

        return params;
    }

    /**
     * Parses individual annotation values, handling different types.
     */
    private static Object parseValue(Object value) {
        if (value instanceof Type) {
            // Class values are represented as Type objects
            return ((Type) value).getClassName();
        } else if (value instanceof String[]) {
            // Enum values are represented as [descriptor, value]
            String[] enumValue = (String[]) value;
            return enumValue.length > 1 ? enumValue[1] : enumValue[0];
        } else if (value instanceof AnnotationNode) {
            // Nested annotation
            return parseAnnotationValues((AnnotationNode) value);
        } else if (value instanceof List) {
            // Array values
            List<?> list = (List<?>) value;
            List<Object> parsedList = new ArrayList<>();
            for (Object item : list) {
                parsedList.add(parseValue(item));
            }
            return parsedList;
        }
        // Primitive values and Strings are returned as-is
        return value;
    }
}
