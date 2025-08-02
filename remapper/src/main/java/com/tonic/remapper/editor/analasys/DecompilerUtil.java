package com.tonic.remapper.editor.analasys;

import static com.tonic.remapper.editor.analasys.SpoonPipeline.*;
import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.*;
import org.objectweb.asm.*;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.*;

import java.io.StringWriter;

public final class DecompilerUtil {

    private DecompilerUtil() {}

    /**
     * @param owner  the full ASM ClassNode of the original class
     * @param target the MethodNode you want to view
     * @return pretty-printed Java source of that method (as a String)
     */
    public static String decompile(ClassNode owner, MethodNode target, boolean deobfuscate) {
        ClassNode stub = new ClassNode(Opcodes.ASM9);

        stub.version    = owner.version;
        stub.access     = Opcodes.ACC_PUBLIC;
        stub.name       = owner.name + "$DecompilerStub";
        stub.superName  = (owner.superName == null ? "java/lang/Object"
                : owner.superName);
        stub.interfaces = java.util.List.of();

        MethodNode copy = cloneMethod(target);
        stub.methods.add(copy);

        boolean needsCtor = ( (target.access & Opcodes.ACC_STATIC) == 0 ) && !copy.name.equals("<init>");
        if (needsCtor) {
            MethodVisitor mv = stub.visitMethod(Opcodes.ACC_PUBLIC,
                    "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    stub.superName,
                    "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        if(deobfuscate) {
            try {
                DeobPipeline.create()
                        .add(BytecodeTransformers.constantFolding())
                        .add(BytecodeTransformers.deadCodeElimination())
                        .add(BytecodeTransformers.stripTryCatch())
                        .run(copy);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        stub.accept(cw);
        byte[] bytes = cw.toByteArray();

        InMemoryTypeLoader memLoader = new InMemoryTypeLoader();
        String internalName = stub.name;
        memLoader.addType(internalName, bytes);

        ITypeLoader ctx = new CompositeTypeLoader(
                memLoader,
                new InputTypeLoader()
        );

        DecompilerSettings settings = DecompilerSettings.javaDefaults();
        settings.setTypeLoader(ctx);

        StringWriter out = new StringWriter();
        Decompiler.decompile(internalName, new PlainTextOutput(out), settings);

        String fullSrc = out.toString().replace(owner.name + "$DecompilerStub", owner.name);

        if(deobfuscate) {
            try {
                fullSrc = SpoonPipeline.create()
                        .add(new OpaquePredicateCleaner())
                        .run(owner.name, fullSrc);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return fullSrc + '\n';
    }

    private static MethodNode cloneMethod(MethodNode original) {
        MethodNode clone = new MethodNode(
                original.access,
                original.name,
                original.desc,
                original.signature,
                original.exceptions == null ? null
                        : original.exceptions.toArray(String[]::new)
        );
        original.accept(clone);
        return clone;
    }
}