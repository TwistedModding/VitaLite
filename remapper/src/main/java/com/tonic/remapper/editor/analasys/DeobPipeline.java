package com.tonic.remapper.editor.analasys;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

/**
 * A collection of *very* small, generic de-obfuscation passes that can be
 * chained together.  None of them are perfect, but they clean up the cruft
 * you meet most often in obfuscated jars.
 *
 * Usage:
 * --------
 *   MethodNode mn = ...;
 *   ClassNode  cn = ...;      // owning class (needed for locals rename)
 *
 *   DeobPipeline
 *       .create()
 *       .add(Transformers.constantFolding())
 *       .add(Transformers.deadCodeElimination())
 *       .add(Transformers.stripTryCatch())
 *       .add(Transformers.renameLocals(cn))
 *       .run(mn);
 */
public final class DeobPipeline {

    @FunctionalInterface
    public interface MethodTransformer {
        /** Mutates the supplied MethodNode in place. */
        void transform(MethodNode mn) throws AnalyzerException;
    }

    // -----------------------------------------------------------------
    // Pipeline helper
    // -----------------------------------------------------------------
    private final List<MethodTransformer> chain = new ArrayList<>();

    public static DeobPipeline create() { return new DeobPipeline(); }

    public DeobPipeline add(MethodTransformer t) {
        chain.add(t);
        return this;
    }

    public void run(MethodNode mn) {
        for (MethodTransformer t : chain) {
            try { t.transform(mn); }
            catch (AnalyzerException ex) {
                System.err.println("[deob] pass failed, skipping: " + ex);
            }
        }
    }
}
