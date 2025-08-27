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
            else if(literal.isInteger() && literal.getIntValue() == 2048)
            {
                System.out.println("Patching PCM Load");
                patchPcmLoad(literal);
            }
        }

        @Override
        public void edit(FieldAccess access) {
            if (method == null)
                method = access.getMethod();

            if(!access.getFieldName().equals(fieldName) || !access.getFieldOwner().equals(fieldOwner))
                return;

            AbstractInsnNode target = access.getFieldInstruction();

            //TODO: FIXY FIX
//            if(target.getPrevious().getOpcode() == Opcodes.DUP)
//            {
//                AbstractInsnNode start = target.getPrevious().getPrevious();
//                if(start.getOpcode() != Opcodes.NEW)
//                    return;
//                System.out.println("Patching PCM Init");
//                while(start.getOpcode() != Opcodes.PUTSTATIC)
//                {
//                    if(!(start instanceof LabelNode) && !(start instanceof LineNumberNode))
//                        toRemove.add(start);
//                    start = start.getNext();
//                }
//                toRemove.add(start); // include the putstatic
//                return;
//            }
//
//            AbstractInsnNode target2 = target.getNext();
//            if(target2.getOpcode() != Opcodes.ALOAD)
//                return;
//            AbstractInsnNode target3 = target2.getNext();
//            AbstractInsnNode target4 = target3.getNext();
//            toRemove.add(target);
//            toRemove.add(target2);
//            toRemove.add(target3);
//            toRemove.add(target4);
        }

        private void patchSoundLoad(LiteralValue literal)
        {
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
