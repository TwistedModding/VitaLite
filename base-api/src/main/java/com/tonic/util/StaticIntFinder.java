package com.tonic.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;

public class StaticIntFinder
{
    /**
     * Returns "pkg.Outer.Inner.FIELD" (dots for $), or null if not found.
     */
    public static String find(Class<?> root, int target) {
        if (root == null) return null;

        final Deque<Class<?>> stack = new ArrayDeque<>(8);
        stack.push(root);

        while (!stack.isEmpty()) {
            final Class<?> cls = stack.pop();

            Class<?>[] inners;
            try {
                inners = cls.getDeclaredClasses();
            } catch (Throwable t) {
                inners = null;
            }
            if (inners != null) {
                for (Class<?> inner : inners) {
                    stack.push(inner);
                }
            }

            Field[] fields;
            try {
                fields = cls.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }

            final int classMods = cls.getModifiers();
            final boolean classPublic = Modifier.isPublic(classMods);

            for (final Field f : fields) {
                final int mods = f.getModifiers();
                if ((mods & Modifier.STATIC) == 0) continue;
                if (f.getType() != int.class) continue;
                if (f.isSynthetic()) continue;

                if (classPublic && Modifier.isPublic(mods)) {
                    try {
                        if (f.getInt(null) == target) {
                            return qualify(root, cls, f.getName());
                        }
                    } catch (IllegalAccessException ignored) {
                    }
                }

                if (!f.canAccess(null)) {
                    if (!f.trySetAccessible()) {
                        continue;
                    }
                }

                try {
                    if (f.getInt(null) == target) {
                        return qualify(root, cls, f.getName());
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return target + "";
    }

    private static String qualify(Class<?> root, Class<?> cls, String fieldName) {
        final String n = cls.getName();
        final String r = root.getName();
        String simple = n.indexOf('$') >= 0 ? n.replace('$', '.') : n;
        String simpleRoot = r.indexOf('$') >= 0 ? r.replace('$', '.') : r;
        return (simple + "." + fieldName).replace(simpleRoot + ".", "");
    }
}
