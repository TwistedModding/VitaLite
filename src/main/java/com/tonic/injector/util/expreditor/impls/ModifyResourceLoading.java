package com.tonic.injector.util.expreditor.impls;

import com.tonic.injector.util.BytecodeBuilder;
import com.tonic.injector.util.expreditor.ExprEditor;
import com.tonic.injector.util.expreditor.FieldAccess;
import com.tonic.injector.util.expreditor.LiteralValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class ModifyResourceLoading extends ExprEditor
{
    private final List<AbstractInsnNode> toRemove = new ArrayList<>();
    private final ExprEditor editor = new ExprEditor() {
        private MethodNode method;
        private String fieldName = null;
        private String fieldOwner = null;

        @Override
        public void edit(LiteralValue literal) {
            if (method == null)
                method = literal.getMethod();

            if (literal.isString()) {
                switch (literal.getStringValue()) {
                    case "Sound FX":
                    case "Music Tracks":
                    case "Music Samples":
                    case "Music Patches":
                        patchSoundLoad(literal);
                        break;
                }
            }
        }

        private void patchSoundLoad(LiteralValue literal)
        {
            System.out.println("Patching load of: " + literal.getStringValue());
            InsnList insns = BytecodeBuilder.create()
                    .pop2()
                    .pop()
                    .build();

            AbstractInsnNode target = literal.getLiteralInstruction().getNext();
            AbstractInsnNode methodCall = target.getNext();
            toRemove.add(methodCall);
            method.instructions.insert(target, insns);
        }

        private void patchPcmLoad(LiteralValue literal)
        {
            InsnList insns = BytecodeBuilder.create()
                    .pop2()
                    .pop2()
                    .pushNull()
                    .build();

            AbstractInsnNode target = literal.getLiteralInstruction().getNext();
            if(!(target.getNext() instanceof MethodInsnNode))
                return;
            MethodInsnNode methodCall = (MethodInsnNode) target.getNext();
            FieldInsnNode field = (FieldInsnNode) methodCall.getNext();
            fieldName = field.name;
            fieldOwner = field.owner;
            toRemove.add(methodCall);
            method.instructions.insert(target, insns);
        }
    };

    @Override
    public void edit(LiteralValue literal) {
        if (!literal.isString() || !literal.getStringValue().equals("Sound FX"))
            return;

        System.out.println("Patching Sound Load: " + literal.getClassNode().name + "." + literal.getMethod().name);

        editor.instrument(literal.getClassNode(), literal.getMethod());
        for(AbstractInsnNode insn : toRemove)
        {
            literal.getMethod().instructions.remove(insn);
        }
        toRemove.clear();
    }
}
