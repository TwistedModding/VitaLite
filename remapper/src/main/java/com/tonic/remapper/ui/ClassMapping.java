package com.tonic.remapper.ui;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ClassMapping {
    final String originalName; // internal name from class node
    String newName; // user-assigned
    final List<MethodRecord> methods = new ArrayList<>();
    final List<FieldRecord> fields = new ArrayList<>();
    final Map<String, String> methodMap = new LinkedHashMap<>(); // sig -> friendly
    final Map<String, String> fieldMap = new LinkedHashMap<>(); // sig -> friendly

    ClassMapping(ClassNode cn, Set<MethodKey> usedMethods) {
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