package com.tonic.remapper.classes;

import java.util.Map;

/**
 * Simple match result between an old and new class fingerprint.
 */
public class ClassMatch {
    public final ClassFingerprint oldFp;
    public final ClassFingerprint newFp;
    public double similarity;
    public final Map<String, Double> breakdown; // detailed component scores

    public ClassMatch(ClassFingerprint oldFp, ClassFingerprint newFp) {
        this.oldFp = oldFp;
        this.newFp = newFp;
        this.similarity = oldFp.similarity(newFp);
        this.breakdown = oldFp.componentSimilarityBreakdown(newFp);
    }

    @Override
    public String toString() {
        return String.format("ClassMatch{old=%s, new=%s, sim=%.3f}", oldFp.internalName, newFp.internalName, similarity);
    }
}