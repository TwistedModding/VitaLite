package com.tonic.remapper.editor.analasys;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public final class DeobPipeline {

    @FunctionalInterface
    public interface MethodTransformer {
        void transform(MethodNode mn) throws AnalyzerException;
    }

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
