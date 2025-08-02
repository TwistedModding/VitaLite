package com.tonic.remapper.editor;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ClassMapping {
    public final ClassNode classNode;
    public final String originalName; // internal name from class node
    public String newName; // user-assigned
    public final List<MethodRecord> methods = new ArrayList<>();
    public final List<FieldRecord> fields = new ArrayList<>();
    public final Map<String, String> methodMap = new LinkedHashMap<>(); // sig -> friendly
    public final Map<String, String> fieldMap = new LinkedHashMap<>(); // sig -> friendly

    public ClassMapping(ClassNode cn, Set<MethodKey> usedMethods) {
        this.classNode = cn;
        this.originalName = cn.name;
        for (MethodNode mn : cn.methods) {
            MethodKey mk = new MethodKey(cn.name, mn.name, mn.desc);
            if (usedMethods.contains(mk)) {
                MethodRecord mr = new MethodRecord(mn, this);
                methods.add(mr);
            }
        }
        for (FieldNode fn : cn.fields) {
            FieldRecord fr = new FieldRecord(fn, this);
            fields.add(fr);
        }
    }

    @Override
    public String toString() {
        if (newName != null && !newName.isBlank()) {
            return newName + " [" + originalName + "]";
        }
        return originalName;
    }
}