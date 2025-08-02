package com.tonic.remapper.fields;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

/**
 * Scans every method of every class and records which methods read/write each
 * field.  This lets us correlate fields through the *methods that already map*.
 */
public class FieldUsage {
    public static Map<FieldKey, Set<MethodKey>> build(List<ClassNode> classes) {
        Map<FieldKey, Set<MethodKey>> out = new HashMap<>();

        for (ClassNode cn : classes) {
            for (MethodNode mn : cn.methods) {
                MethodKey mk = new MethodKey(cn.name, mn.name, mn.desc);

                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (!(insn instanceof FieldInsnNode)) continue;
                    FieldInsnNode fi = (FieldInsnNode) insn;
                    FieldKey fk = new FieldKey(fi.owner, fi.name, fi.desc);
                    out.computeIfAbsent(fk, __ -> new HashSet<>()).add(mk);
                }
            }
        }
        return out;
    }
}