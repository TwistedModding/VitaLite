package com.tonic.injector.types;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class CopyMethodVisitor extends MethodVisitor
{
    private final String mixinInternal;
    private final String gamepackInternal;

    public CopyMethodVisitor(int api, MethodVisitor methodVisitor, ClassNode mixinClass, ClassNode gamepackClass) {
        super(api, methodVisitor);
        mixinInternal = mixinClass.name;
        gamepackInternal = gamepackClass.name;;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (owner.equals(mixinInternal)) {
            super.visitFieldInsn(opcode, gamepackInternal, name, descriptor);
        } else {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (owner.equals(mixinInternal)) {
            super.visitMethodInsn(opcode, gamepackInternal, name, descriptor, isInterface);
        } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (type.equals(mixinInternal)) {
            super.visitTypeInsn(opcode, gamepackInternal);
        } else {
            super.visitTypeInsn(opcode, type);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof Type) {
            Type type = (Type) value;
            if (type.getInternalName().equals(mixinInternal)) {
                super.visitLdcInsn(Type.getObjectType(gamepackInternal));
            } else {
                super.visitLdcInsn(value);
            }
        } else {
            super.visitLdcInsn(value);
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        String transformed = descriptor.replace("L" + mixinInternal + ";", "L" + gamepackInternal + ";");
        super.visitMultiANewArrayInsn(transformed, numDimensions);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        String transformedDesc = descriptor.replace("L" + mixinInternal + ";", "L" + gamepackInternal + ";");
        String transformedSig = signature != null ? signature.replace("L" + mixinInternal + ";", "L" + gamepackInternal + ";") : null;
        super.visitLocalVariable(name, transformedDesc, transformedSig, start, end, index);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null && type.equals(mixinInternal)) {
            super.visitTryCatchBlock(start, end, handler, gamepackInternal);
        } else {
            super.visitTryCatchBlock(start, end, handler, type);
        }
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        Object[] transformedLocal = transformFrame(local, numLocal);
        Object[] transformedStack = transformFrame(stack, numStack);
        super.visitFrame(type, numLocal, transformedLocal, numStack, transformedStack);
    }

    private Object[] transformFrame(Object[] frame, int length) {
        if (frame == null) return null;
        Object[] transformed = new Object[length];
        for (int i = 0; i < length; i++) {
            if (frame[i] instanceof String && frame[i].equals(mixinInternal)) {
                transformed[i] = gamepackInternal;
            } else {
                transformed[i] = frame[i];
            }
        }
        return transformed;
    }
}