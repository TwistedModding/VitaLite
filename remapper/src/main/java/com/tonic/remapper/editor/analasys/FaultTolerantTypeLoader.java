package com.tonic.remapper.editor.analasys;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ITypeLoader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

public class FaultTolerantTypeLoader implements ITypeLoader {
    private final ITypeLoader primary;
    private final ITypeLoader fallback;
    private final Set<String> missingTypes = new HashSet<>();

    public FaultTolerantTypeLoader(ITypeLoader primary) {
        this.primary = primary;
        this.fallback = new InputTypeLoader();
    }

    @Override
    public boolean tryLoadType(String internalName, Buffer buffer) {
        // First try the primary loader (our in-memory class)
        if (primary.tryLoadType(internalName, buffer)) {
            return true;
        }

        // Then try the fallback (classpath)
        if (fallback.tryLoadType(internalName, buffer)) {
            return true;
        }

        // If both fail, create a minimal stub class
        if (!missingTypes.contains(internalName)) {
            missingTypes.add(internalName);
            //System.err.println("Warning: Creating stub for missing type: " + internalName);
        }

        // Create a minimal valid class file for the missing type
        byte[] stubBytes = createStubClass(internalName);
        buffer.reset(stubBytes.length);
        buffer.putByteArray(stubBytes, 0, stubBytes.length);
        buffer.position(0);

        return true;
    }

    /**
     * Creates a minimal stub class for a missing type
     */
    private byte[] createStubClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);

        // Determine if this looks like an interface based on naming conventions
        boolean isInterface = internalName.contains("$") ||
                internalName.endsWith("able") ||
                internalName.startsWith("I") ||
                internalName.contains("Listener") ||
                internalName.contains("Handler");

        int access = Opcodes.ACC_PUBLIC;
        if (isInterface) {
            access |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        }

        String superName = isInterface ? "java/lang/Object" : "java/lang/Object";
        String[] interfaces = isInterface ? new String[0] : null;

        cw.visit(Opcodes.V1_8, access, internalName, null, superName, interfaces);

        // Add a default constructor for non-interfaces
        if (!isInterface) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }
}
