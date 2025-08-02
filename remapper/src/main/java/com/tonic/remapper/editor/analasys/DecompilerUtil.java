package com.tonic.remapper.editor.analasys;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.*;
import com.tonic.remapper.editor.analasys.transformers.BytecodeTransformers;
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

        // 1) build a brand-new ClassNode
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

        String fullSrc = out.toString();

        String sigRegex  = "\\b" + java.util.regex.Pattern.quote(target.name) + "\\s*\\(";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(sigRegex);
        java.util.regex.Matcher  m = p.matcher(fullSrc);
        if (!m.find()) return fullSrc.replace(owner.name + "$DecompilerStub", owner.name);

        int start = fullSrc.lastIndexOf('\n', m.start()) + 1;
        int depth = 0;
        int i = m.start();
        for (; i < fullSrc.length(); i++) {
            char c = fullSrc.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { i++; break; }
            }
        }
        return stripExtraIndent(fullSrc.substring(start, i).trim()) + '\n';
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

    /**
     * Removes the smallest common leading indentation that is > 0.
     * The very first line of many decompilers is already flush-left, so we
     * purposely ignore lines whose indent is 0 when computing the minimum.
     */
    public static String stripExtraIndent(String code) {
        String[] lines = code.split("\\R", -1);      // keep trailing blank line
        int common = Integer.MAX_VALUE;

        // 1) find the smallest indent that is *positive* on any non-blank line
        for (String ln : lines) {
            if (ln.trim().isEmpty()) continue;       // skip blank
            int i = 0;
            while (i < ln.length() && Character.isWhitespace(ln.charAt(i))) i++;
            if (i == 0) continue;                    // ignore flush-left lines
            common = Math.min(common, i);
        }
        if (common == Integer.MAX_VALUE)            // every line was flush-left
            return code;

        // 2) strip that indent from every line that is long enough
        StringBuilder out = new StringBuilder(code.length());
        for (String ln : lines) {
            out.append(ln.length() >= common ? ln.substring(common) : ln)
                    .append('\n');
        }
        return out.toString();
    }
}