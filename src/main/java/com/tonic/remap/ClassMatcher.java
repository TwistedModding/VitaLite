package com.tonic.remap;

import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.stream.Collectors;

public class ClassMatcher {

    /**
     * Simple match result between an old and new class fingerprint.
     */
    public static final class ClassMatch {
        public final ClassFingerprint oldFp;
        public final ClassFingerprint newFp;
        public final double similarity;
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

    /**
     * For each old class, returns the topK new classes by fingerprint similarity above minSimilarity.
     */
    public static List<ClassMatch> matchClassesTopK(
            Collection<ClassNode> oldClassNodes,
            Collection<ClassNode> newClassNodes,
            int topK,
            double minSimilarity
    ) {
        // Precompute fingerprints
        Map<String, ClassFingerprint> oldFps = oldClassNodes.stream()
                .collect(Collectors.toMap(cn -> cn.name, ClassFingerprint::fromClassNode));
        Map<String, ClassFingerprint> newFps = newClassNodes.stream()
                .collect(Collectors.toMap(cn -> cn.name, ClassFingerprint::fromClassNode));

        List<ClassMatch> results = new ArrayList<>();

        for (ClassFingerprint oldFp : oldFps.values()) {
            PriorityQueue<ClassMatch> pq = new PriorityQueue<>(Comparator.comparingDouble(cm -> -cm.similarity));
            for (ClassFingerprint newFp : newFps.values()) {
                ClassMatch match = new ClassMatch(oldFp, newFp);
                if (match.similarity >= minSimilarity) {
                    pq.add(match);
                }
            }
            int taken = 0;
            while (taken < topK && !pq.isEmpty()) {
                results.add(pq.poll());
                taken++;
            }
        }
        return results;
    }

    /**
     * Greedy one-to-one mapping: picks highest similarity pair first, then removes those classes from consideration.
     * Returns map oldInternalName -> newInternalName.
     */
    public static Map<String, String> greedyOneToOneMatch(
            Collection<ClassNode> oldClassNodes,
            Collection<ClassNode> newClassNodes,
            double minSimilarity
    ) {
        Map<String, ClassFingerprint> oldFps = oldClassNodes.stream()
                .collect(Collectors.toMap(cn -> cn.name, ClassFingerprint::fromClassNode));
        Map<String, ClassFingerprint> newFps = newClassNodes.stream()
                .collect(Collectors.toMap(cn -> cn.name, ClassFingerprint::fromClassNode));

        // Build all candidate matches above threshold
        List<ClassMatch> allMatches = new ArrayList<>();
        for (ClassFingerprint oldFp : oldFps.values()) {
            for (ClassFingerprint newFp : newFps.values()) {
                ClassMatch match = new ClassMatch(oldFp, newFp);
                if (match.similarity >= minSimilarity) {
                    allMatches.add(match);
                }
            }
        }

        // Sort descending similarity
        allMatches.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        Map<String, String> mapping = new HashMap<>();
        Set<String> usedOld = new HashSet<>();
        Set<String> usedNew = new HashSet<>();

        for (ClassMatch m : allMatches) {
            if (usedOld.contains(m.oldFp.internalName) || usedNew.contains(m.newFp.internalName)) continue;
            mapping.put(m.oldFp.internalName, m.newFp.internalName);
            usedOld.add(m.oldFp.internalName);
            usedNew.add(m.newFp.internalName);
        }

        return mapping;
    }
}
