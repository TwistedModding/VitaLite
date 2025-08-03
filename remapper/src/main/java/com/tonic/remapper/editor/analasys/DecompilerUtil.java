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
        ClassNode stub = isolateMethod(owner, target);
        MethodNode copy = stub.methods.stream()
                .filter(m -> m.name.equals(target.name) && m.desc.equals(target.desc))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Method not found in stub"));

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

        String fullSrc = decompile(stub, owner);

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

    public static String decompile(ClassNode owner, MethodNode target)
    {
        ClassNode stub = isolateMethod(owner, target);
        MethodNode copy = stub.methods.stream()
                .filter(m -> m.name.equals(target.name) && m.desc.equals(target.desc))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Method not found in stub"));

        try {
            DeobPipeline.create()
                    .add(BytecodeTransformers.constantFolding())
                    .add(BytecodeTransformers.deadCodeElimination())
                    .add(BytecodeTransformers.stripTryCatch())
                    .run(copy);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return decompile(stub, owner);
    }

    public static String decompile(ClassNode stub, ClassNode owner)
    {
        return decompile(stub).replace(owner.name + "$DecompilerStub", owner.name);
    }

    public static String decompile(ClassNode classNode)
    {
        byte[] bytes;
        try {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    // If we can't determine the common superclass, just return Object
                    try {
                        return super.getCommonSuperClass(type1, type2);
                    } catch (Exception e) {
                        // Can't find one of the types, assume Object is the common superclass
                        return "java/lang/Object";
                    }
                }
            };
            classNode.accept(cw);
            bytes = cw.toByteArray();
        }
        catch (Exception e)
        {
            System.out.println("Failed to write class: " + classNode.name);
            e.printStackTrace();
            System.exit(0);
            return null;
        }

        InMemoryTypeLoader memLoader = new InMemoryTypeLoader();
        String internalName = classNode.name;
        memLoader.addType(internalName, bytes);

        // Create a fault-tolerant type loader that won't crash on missing types
        ITypeLoader ctx = new FaultTolerantTypeLoader(memLoader);

        DecompilerSettings settings = DecompilerSettings.javaDefaults();
        settings.setTypeLoader(ctx);

        // Additional settings to handle missing types more gracefully
        settings.setShowSyntheticMembers(false);
        settings.setForceExplicitImports(true);

        StringWriter out = new StringWriter();

        try {
            Decompiler.decompile(
                    internalName,
                    new PlainTextOutput(out),
                    settings);
        } catch (Exception e) {
            // If decompilation still fails, return a minimal representation
            out.append("// Decompilation failed for class: ").append(internalName).append("\n");
            out.append("// Error: ").append(e.getMessage()).append("\n");
            out.append("public class ").append(internalName.replace('/', '_')).append(" {\n");
            out.append("    // Unable to decompile class body\n");
            out.append("}\n");
        }

        return out.toString();
    }

    public static ClassNode isolateMethod(ClassNode owner, MethodNode target) {
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
        return stub;
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