package com.tonic.remapper.garbage;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.HashMap;
import java.util.Map;

public class OpaquePredicateScanner
{
    public static Map<MethodKey, Number> scan(Map<MethodKey, MethodNode> used)
    {
        Map<MethodKey, Number> opaquePredicates = new HashMap<>();

        for(var entry : used.entrySet())
        {
            MethodKey key = entry.getKey();
            MethodNode methodNode = entry.getValue();
            if (methodNode == null)
            {
                continue;
            }
            key.hasGarbage = throwsISE(methodNode) || !lastParameterIsUsed(methodNode);
            opaquePredicates.put(key, null);
        }

        for(var entry : used.entrySet())
        {
            MethodKey key = entry.getKey();
            if (!key.hasGarbage)
                continue;

            MethodNode methodNode = entry.getValue();
            if (methodNode == null)
                continue;

            opaquePredicates.put(key, OpaqueExpressionEvaluator.calculatePassableValue(methodNode));
        }

        return opaquePredicates;
    }

    private static boolean throwsISE(MethodNode methodNode)
    {
        for(AbstractInsnNode insn : methodNode.instructions)
        {
            if(!(insn instanceof MethodInsnNode))
                continue;
            MethodInsnNode methodInsn = (MethodInsnNode) insn;

            if(methodInsn.owner.equals("java/lang/IllegalStateException"))
                return true;
        }
        return false;
    }

    public static boolean lastParameterIsUsed(MethodNode mn) {
        Type[] args = Type.getArgumentTypes(mn.desc);

        if (args.length == 0)
            return true;

        int slot = ((mn.access & Opcodes.ACC_STATIC) == 0) ? 1 : 0;
        for (int i = 0; i < args.length - 1; i++)
            slot += args[i].getSize();

        final int lastParamSlot = slot;
        for (AbstractInsnNode insn = mn.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            int op = insn.getOpcode();
            if (op < 0) continue;

            switch (op) {
                case Opcodes.ILOAD:
                case Opcodes.LLOAD:
                case Opcodes.FLOAD:
                case Opcodes.DLOAD:
                case Opcodes.ALOAD:
                    if (((VarInsnNode) insn).var == lastParamSlot)
                        return true;
                    break;
                case Opcodes.IINC:
                    if (((IincInsnNode) insn).var == lastParamSlot)
                        return true;
                    break;

                default:
                    break;
            }
        }
        return false;
    }
}
