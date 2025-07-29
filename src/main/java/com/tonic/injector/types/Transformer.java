package com.tonic.injector.types;

import org.objectweb.asm.tree.*;

public abstract class Transformer
{
    public ClassNode transform(ClassNode node) {
        node.methods.forEach(this::transform);
        node.fields.forEach(this::transform);
        if(node.visibleAnnotations != null)
        {
            node.visibleAnnotations.forEach(this::transform);
        }
        if(node.invisibleAnnotations != null)
        {
            node.invisibleAnnotations.forEach(this::transform);
        }
        if(node.visibleTypeAnnotations != null)
        {
            node.visibleTypeAnnotations.forEach(this::transform);
        }
        if(node.invisibleTypeAnnotations != null)
        {
            node.invisibleTypeAnnotations.forEach(this::transform);
        }
        node.innerClasses.forEach(this::transform);
        return node;
    }

    public InnerClassNode transform(InnerClassNode node) {
        return node;
    }



    public MethodNode transform(MethodNode node) {
        node.instructions = transform(node.instructions);
        node.tryCatchBlocks.forEach(this::transform);
        if(node.localVariables != null)
        {
            node.localVariables.forEach(this::transform);
        }
        if(node.visibleAnnotations != null)
        {
            node.visibleAnnotations.forEach(this::transform);
        }
        if(node.invisibleAnnotations != null)
        {
            node.invisibleAnnotations.forEach(this::transform);
        }
        if(node.visibleTypeAnnotations != null)
        {
            node.visibleTypeAnnotations.forEach(this::transform);
        }
        if(node.invisibleTypeAnnotations != null)
        {
            node.invisibleTypeAnnotations.forEach(this::transform);
        }
        return node;
    }

    public FieldNode transform(FieldNode node) {
        if(node.visibleAnnotations != null)
        {
            node.visibleAnnotations.forEach(this::transform);
        }
        if(node.invisibleAnnotations != null)
        {
            node.invisibleAnnotations.forEach(this::transform);
        }
        if(node.visibleTypeAnnotations != null)
        {
            node.visibleTypeAnnotations.forEach(this::transform);
        }
        if(node.invisibleTypeAnnotations != null)
        {
            node.invisibleTypeAnnotations.forEach(this::transform);
        }
        return node;
    }

    public AnnotationNode transform(AnnotationNode node) {
        return node;
    }

    public ParameterNode transform(ParameterNode node) {
        return node;
    }

    public TryCatchBlockNode transform(TryCatchBlockNode node) {
        return node;
    }

    public LocalVariableNode transform(LocalVariableNode node) {
        return node;
    }

    public LineNumberNode transform(LineNumberNode node) {
        return node;
    }

    public FrameNode transform(FrameNode node) {
        return node;
    }

    public TypeAnnotationNode transform(TypeAnnotationNode node) {
        return node;
    }

    public InsnList transform(InsnList node) {
        for (AbstractInsnNode insn : node) {
            if(insn instanceof LabelNode)
            {
                transform(node, (LabelNode) insn);
            }
            else if(insn instanceof MethodInsnNode)
            {
                transform(node, (MethodInsnNode) insn);
            }
            else if(insn instanceof FieldInsnNode)
            {
                transform(node, (FieldInsnNode) insn);
            }
            else if(insn instanceof TypeInsnNode)
            {
                transform(node, (TypeInsnNode) insn);
            }
            else if(insn instanceof VarInsnNode)
            {
                transform(node, (VarInsnNode) insn);
            }
            else if(insn instanceof IntInsnNode)
            {
                transform(node, (IntInsnNode) insn);
            }
            else if(insn instanceof LdcInsnNode)
            {
                transform(node, (LdcInsnNode) insn);
            }
            else if(insn instanceof IincInsnNode)
            {
                transform(node, (IincInsnNode) insn);
            }
            else if(insn instanceof InvokeDynamicInsnNode)
            {
                transform(node, (InvokeDynamicInsnNode) insn);
            }
            else if(insn instanceof MultiANewArrayInsnNode)
            {
                transform(node, (MultiANewArrayInsnNode) insn);
            }
            else if(insn instanceof TableSwitchInsnNode)
            {
                transform(node, (TableSwitchInsnNode) insn);
            }
            else if(insn instanceof LookupSwitchInsnNode)
            {
                transform(node, (LookupSwitchInsnNode) insn);
            }
            else if(insn instanceof JumpInsnNode)
            {
                transform(node, (JumpInsnNode) insn);
            }
            else if(insn instanceof InsnNode)
            {
                transform(node, (InsnNode) insn);
            }
        }
        return node;
    }

    public LabelNode transform(InsnList list, LabelNode node) {
        return node;
    }

    public MethodInsnNode transform(InsnList list, MethodInsnNode node) {
        return node;
    }

    public FieldInsnNode transform(InsnList list, FieldInsnNode node) {
        return node;
    }

    public TypeInsnNode transform(InsnList list, TypeInsnNode node) {
        return node;
    }

    public VarInsnNode transform(InsnList list, VarInsnNode node) {
        return node;
    }

    public IntInsnNode transform(InsnList list, IntInsnNode node) {
        return node;
    }

    public LdcInsnNode transform(InsnList list, LdcInsnNode node) {
        return node;
    }

    public IincInsnNode transform(InsnList list, IincInsnNode node) {
        return node;
    }

    public InvokeDynamicInsnNode transform(InsnList list, InvokeDynamicInsnNode node) {
        return node;
    }

    public MultiANewArrayInsnNode transform(InsnList list, MultiANewArrayInsnNode node) {
        return node;
    }

    public TableSwitchInsnNode transform(InsnList list, TableSwitchInsnNode node) {
        return node;
    }

    public LookupSwitchInsnNode transform(InsnList list, LookupSwitchInsnNode node) {
        return node;
    }

    public JumpInsnNode transform(InsnList list, JumpInsnNode node) {
        return node;
    }

    public InsnNode transform(InsnList list, InsnNode node) {
        return node;
    }
}
