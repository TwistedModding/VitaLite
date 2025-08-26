package com.tonic.injector.util.expreditor.impls;

import com.tonic.injector.util.expreditor.ExprEditor;
import com.tonic.injector.util.expreditor.LiteralValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

public class IntegerLiteralReplacer extends ExprEditor {
    private final int oldNum;
    public IntegerLiteralReplacer(int oldNum)
    {
        this.oldNum = oldNum;
    }
    @Override
    public void edit(LiteralValue literal) {
        if (literal.isInteger() && literal.getIntValue() != null &&
                literal.getIntValue() == oldNum) {

            // Replace with 0
            InsnList replacement = new InsnList();
            replacement.add(new InsnNode(Opcodes.ICONST_0));
            literal.replace(replacement);
        }
    }
}