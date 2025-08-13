package com.tonic.remapper.editor.analasys;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnclosureRebuilder {
    static void process(List<ClassNode> all) {
        Map<String, ClassNode> byName = new HashMap<>();
        for (ClassNode cn : all) byName.put(cn.name, cn);

        // Index where each class gets allocated (to find EnclosingMethod)
        Map<String, List<EncloseSite>> allocs = new HashMap<>();

        for (ClassNode owner : all) {
            for (MethodNode m : owner.methods) {
                AbstractInsnNode insn = m.instructions.getFirst();
                while (insn != null) {
                    if (insn.getOpcode() == Opcodes.NEW) {
                        String newType = ((TypeInsnNode) insn).desc;
                        AbstractInsnNode next = insn.getNext();
                        // (NEW, DUP, INVOKESPECIAL <init>)
                        if (next != null && next.getOpcode() == Opcodes.DUP) {
                            AbstractInsnNode ctor = next.getNext();
                            if (ctor instanceof MethodInsnNode
                                    && ctor.getOpcode() == Opcodes.INVOKESPECIAL
                                    && ((MethodInsnNode) ctor).name.equals("<init>")
                                    && ((MethodInsnNode) ctor).owner.equals(newType)) {

                                allocs.computeIfAbsent(newType, k -> new ArrayList<>())
                                        .add(new EncloseSite(owner.name, m.name, m.desc));
                            }
                        }
                    }
                    insn = insn.getNext();
                }
            }
        }

        // For each candidate inner/anonymous class, rebuild attributes
        for (ClassNode inner : all) {
            List<EncloseSite> sites = allocs.get(inner.name);
            if (sites == null || sites.isEmpty()) continue;

            // Heuristic: pick the first site (or refine by your rules)
            EncloseSite site = sites.get(0);

            // 1) EnclosingMethod
            inner.outerClass = site.owner;
            inner.outerMethod = site.method;
            inner.outerMethodDesc = site.desc;

            // 2) InnerClasses entry (anonymous -> inner_name = null)
            // If you can infer a simple name, set it; else null = anonymous
            if (inner.innerClasses == null) inner.innerClasses = new ArrayList<>();
            inner.innerClasses.add(new InnerClassNode(inner.name, site.owner, null, inner.access));

            // 3) Mark classic capture fields as synthetic/final (no renaming!)
            for (FieldNode f : inner.fields) {
                if (f.name.equals("this$0") || f.name.startsWith("val$")) {
                    f.access |= (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL);
                }
            }
        }
    }

    static class EncloseSite {
        final String owner, method, desc;
        EncloseSite(String o, String m, String d) { owner = o; method = m; desc = d; }
    }
}
