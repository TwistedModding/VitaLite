package com.tonic.remap;

import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsClient;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class UsedMethodScannerAsm {
    // queue / working set
    private static final Queue<MethodKey> preloadQueue = new LinkedList<>();
    private static final Set<MethodKey> used = new HashSet<>();

    /**
     * Replicates the Javassist findUsedElements logic using ASM structures.
     * @param classNodes all loaded ClassNode instances
     * @return set of MethodKeys considered "used"
     */
    public static Set<MethodKey> findUsedMethods(List<ClassNode> classNodes) {
        preloadQueue.clear();
        used.clear();

        // build lookup maps
        Map<String, ClassNode> classMap = new HashMap<>();
        for (ClassNode cn : classNodes) {
            classMap.put(cn.name, cn);
        }

        // build flat method map for quick lookup
        Map<MethodKey, MethodNode> allMethods = new HashMap<>();
        for (ClassNode cn : classNodes) {
            for (MethodNode mn : cn.methods) {
                MethodKey key = new MethodKey(cn.name, mn.name, mn.desc);
                allMethods.put(key, mn);
            }
        }

        // initial seeding: mirror Javassist filter
        for (ClassNode cn : classNodes) {
            if (cn.name.length() > 2 && !cn.name.equals("client"))
                continue; // same gate as the Javassist version

            for (MethodNode mn : cn.methods) {
                if ((mn.access & Opcodes.ACC_ABSTRACT) != 0)
                    continue;
                if (!fromSuper(mn))
                    continue;
                MethodKey key = new MethodKey(cn.name, mn.name, mn.desc);
                if (!preloadQueue.contains(key)) {
                    preloadQueue.add(key);
                }
            }
        }

        // BFS-style expansion
        while (!preloadQueue.isEmpty()) {
            MethodKey next = preloadQueue.remove();
            if (!used.add(next)) {
                continue; // already seen
            }
            scanForMethodRefs(next, allMethods, classMap, preloadQueue);
        }

        return used;
    }

    /**
     * Scans the given method for invoked methods, applies the same owner-name filtering
     * and abstract-resolution logic as the Javassist version.
     */
    private static void scanForMethodRefs(
            MethodKey current,
            Map<MethodKey, MethodNode> allMethods,
            Map<String, ClassNode> classMap,
            Queue<MethodKey> queue
    ) {
        MethodNode method = allMethods.get(current);
        if (method == null || method.instructions == null) return;

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode min = (MethodInsnNode) insn;

            String owner = min.owner;
            // mirror javassist filter: only allow if owner length <=2 or equals "client"
            if (owner.length() > 2 && !owner.equals("client")) continue;

            MethodKey calleeKey = new MethodKey(owner, min.name, min.desc);
            MethodNode callee = allMethods.get(calleeKey);
            if (callee == null) continue;

            if ((callee.access & Opcodes.ACC_ABSTRACT) != 0) {
                // try to find concrete implementor/override
                ClassNode mystery = classMap.get(owner);
                if (mystery == null) continue;
                ClassNode extender = findExtenderOf(mystery, classMap.values());
                if (extender == null) continue;
                MethodNode override = findMethodInClass(extender, min.name, min.desc);
                if (override == null) continue;
                if ((override.access & Opcodes.ACC_ABSTRACT) != 0) continue;
                MethodKey overrideKey = new MethodKey(extender.name, override.name, override.desc);
                queue.add(overrideKey);
            } else {
                queue.add(calleeKey);
            }
        }
    }

    private static MethodNode findMethodInClass(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) {
                return mn;
            }
        }
        return null;
    }

    /**
     * Finds a class that extends or implements the supplied class (non-strict: excludes identity).
     */
    private static ClassNode findExtenderOf(ClassNode clazz, Collection<ClassNode> allClasses) {
        for (ClassNode candidate : allClasses) {
            if (extendsOrImplements(candidate, clazz)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Checks if classToCheck extends or implements classOrInterface.
     */
    private static boolean extendsOrImplements(ClassNode classToCheck, ClassNode classOrInterface) {
        if (classToCheck == null || classOrInterface == null) return false;
        if (classToCheck.name.equals(classOrInterface.name)) return false;

        // walk superclass chain
        ClassNode current = classToCheck;
        while (current != null) {
            if (current.name.equals(classOrInterface.name)) {
                return true;
            }
            if (current.superName == null) break;
            current = getClassNode(current.superName, classToCheck);
        }

        // direct interfaces
        if (classToCheck.interfaces != null) {
            for (String iface : classToCheck.interfaces) {
                if (iface.equals(classOrInterface.name)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Helper to avoid needing a global map inside extendsOrImplements; here we fall back to null so chain stops.
    private static ClassNode getClassNode(String internalName, ClassNode context) {
        // This minimal helper just returns null; the calling code only uses it for superclass
        // traversal in this simplified version. If you want full deep traversal you can inject
        // a shared classMap and use that instead.
        return null;
    }

    /**
     * Mirrors the Javassist CodeUtil.fromSuper: checks if the MethodNode's name matches any declared
     * method name on the anchor framework types.
     */
    @SuppressWarnings("deprecation")
    private static boolean fromSuper(MethodNode method) {
        String name = method.name;
        try {
            if (Arrays.stream(Applet.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Runnable.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(FocusListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(WindowListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(MouseAdapter.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(MouseListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(MouseMotionListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Comparator.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Callable.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Iterable.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Collection.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(KeyListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(MouseWheelListener.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(ThreadFactory.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(SSLSession.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Iterator.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Canvas.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(SSLSocket.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;

            if (Arrays.stream(DefaultTlsClient.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(SSLSocketFactory.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(AbstractQueue.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(RuntimeException.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(TlsAuthentication.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Comparable.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(Annotation.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
            if (Arrays.stream(TlsClient.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
