package com.tonic.remapper.buffer;

import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import java.util.HashMap;
import java.util.Map;

public class BufferMatcher
{
    public static void process(String oldBuffer, String newBuffer, Map<MethodKey, MethodNode> oldMethods, Map<MethodKey, MethodNode> newMethods)
    {
        HashMap<String, String> oldSignatures = read(oldBuffer, oldMethods);
        HashMap<String, String> newSignatures = read(newBuffer, newMethods);


        for(var entry : oldSignatures.entrySet())
        {
            System.out.println(entry.getValue() + " :: " + entry.getKey());
        }
    }

    private static HashMap<String,String> read(String className, Map<MethodKey, MethodNode> methods)
    {
        HashMap<String, String> sigs = new HashMap<>();
        for(var entry : methods.entrySet())
        {
            if(!entry.getKey().owner.equals(className))
                continue;

            MethodNode method = entry.getValue();
            if(!shouldProcess(method))
                continue;
            String sig;
            try
            {
                sig = parseSig(method);
            }
            catch (Exception e)
            {
                System.out.println("Failed to parse method " + className + "::" + method.name + method.desc);
                e.printStackTrace();
                continue;
            }
            if(sig.isEmpty())
                continue;

            String name = BufferDef.signatures.get(sig);
            if(name != null)
                sigs.put(name, method.name + method.desc);
        }
        return sigs;
    }

    private static String parseSig(MethodNode mn)
    {
        StringBuilder pattern = new StringBuilder();
        if(mn.desc.endsWith(")V"))
        {
            pattern.append("{V}");
        }
        if(mn.desc.split("\\)")[0].contains("[B"))
        {
            pattern.append("{[B}{").append(Type.getArgumentTypes(mn.desc).length).append("}");
        }

        boolean hadShift = false;
        for(AbstractInsnNode insn : mn.instructions)
        {
            if(insn.getOpcode() == Opcodes.ISHR || insn.getOpcode() == Opcodes.ISHL) {
//                if (hadShift)
//                {
//                    throw new RuntimeException("Unexpected ISHR opcode found in method eg(II)V - " + pattern);
//                }
                hadShift = true;
                String value;
                AbstractInsnNode prev = insn.getPrevious();
                if(prev instanceof IntInsnNode)
                {
                    value = ((IntInsnNode) prev).operand + "";
                }
                else if(prev instanceof LdcInsnNode)
                {
                    value = (int)((LdcInsnNode) prev).cst + "";
                }
                else if(prev.getOpcode() == Opcodes.ICONST_0)
                {
                    value = "~" + 0;
                }
                else if(prev.getOpcode() == Opcodes.ICONST_1)
                {
                    value = "~" + 1;
                }
                else if(prev.getOpcode() == Opcodes.ICONST_2)
                {
                    value = "~" + 2;
                }
                else if(prev.getOpcode() == Opcodes.ICONST_3)
                {
                    value = "~" + 3;
                }
                else if(prev.getOpcode() == Opcodes.ICONST_4)
                {
                    value = "~" + 4;
                }
                else if(prev.getOpcode() == Opcodes.ICONST_5)
                {
                    value = "~" + 5;
                }
                else
                {
                    throw new RuntimeException("Unexpected previous opcode found in method " + mn.name + " - " + pattern + " : " + Printer.OPCODES[prev.getOpcode()] + " at " + prev.getOpcode());
                }
                pattern.append("[").append(value).append("]");
            }
            else if(insn.getOpcode() == Opcodes.BASTORE)
            {
                if(!hadShift)
                {
                    pattern.append("[0]");
                }
                pattern.append("[STORE]");
                hadShift = false;
            }

            if(insn.getOpcode() == Opcodes.ISUB)
            {
                pattern.append("[-]");
            }
            else if(insn.getOpcode() == Opcodes.IADD)
            {
                pattern.append("[+]");
            }
            else if(insn.getOpcode() == Opcodes.ICONST_0)
            {
                pattern.append("[0]");
            }
            else if(insn.getOpcode() == Opcodes.ICONST_1)
            {
                pattern.append("[~1]");
            }
            else if(insn.getOpcode() == Opcodes.ICONST_2)
            {
                pattern.append("[~2]");
            }
            else if(insn.getOpcode() == Opcodes.ICONST_3)
            {
                pattern.append("[~3]");
            }
            else if(insn.getOpcode() == Opcodes.ICONST_4)
            {
                pattern.append("[~4]");
            }
            else if(insn.getOpcode() == Opcodes.ICONST_5)
            {
                pattern.append("[~5]");
            }
            else if(insn instanceof IntInsnNode)
            {
                int value = ((IntInsnNode) insn).operand;
                if(value < 0)
                {
                    pattern.append("[").append(value).append("]");
                }
                else
                {
                    pattern.append("[~").append(value).append("]");
                }
            }
            else if(insn instanceof LdcInsnNode)
            {
                Object cst = ((LdcInsnNode) insn).cst;
                if(cst instanceof Integer)
                {
                    int value = (Integer) cst;
                    if(value == 128 || value == 255)
                    {
                        pattern.append("[*").append(value).append("]");
                    }
                }
            }
        }
        String out = pattern.toString();
        return out; //(out.contains("[8]") || out.equals("[0]")) ? out : null;
    }

    private static boolean shouldProcess(MethodNode node)
    {
        if(node.desc.contains(";") || node.name.length() > 2)
            return false;

        return node.instructions.size() <= 500;
    }
}
