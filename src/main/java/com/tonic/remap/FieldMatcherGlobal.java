package com.tonic.remap;

import java.util.*;
import java.util.stream.Collectors;

public class FieldMatcherGlobal {
    public static class Match {
        public final FieldKey oldKey;
        public final FieldKey newKey;
        public final double score;

        public Match(FieldKey oldKey, FieldKey newKey, double score) {
            this.oldKey = oldKey;
            this.newKey = newKey;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("%s -> %s (score=%.3f)", oldKey, newKey, score);
        }
    }

    /**
     * One-to-one global matching via maximum-weight bipartite assignment.
     *
     * @param oldFields              normalized old fields
     * @param newFields              normalized new fields
     * @param methodMapping          existing method mapping for neighborhood agreement
     * @param classMatchByOldOwner   class matches to softly boost when owners align
     * @param neighborWeight         weight for neighborhood agreement (0..1)
     * @param classOwnerWeight       weight for class-owner alignment (0..1)
     * @param minScoreThreshold      minimum raw score to consider keeping a match
     * @return list of disjoint Match entries forming the one-to-one assignment
     */
    public static List<Match> matchOneToOne(
            Map<FieldKey, NormalizedField> oldFields,
            Map<FieldKey, NormalizedField> newFields,
            Map<MethodKey, MethodKey> methodMapping,
            Map<String, ClassMatcher.ClassMatch> classMatchByOldOwner,
            double neighborWeight,
            double classOwnerWeight,
            double minScoreThreshold
    ) {
        if (oldFields.isEmpty() || newFields.isEmpty()) return Collections.emptyList();

        List<FieldKey> oldList = new ArrayList<>(oldFields.keySet());
        List<FieldKey> newList = new ArrayList<>(newFields.keySet());
        int n = Math.max(oldList.size(), newList.size()); // square for Hungarian

        // Compute base scores and build weight matrix
        double[][] weight = new double[n][n];
        double maxWeight = 0.0;

        for (int i = 0; i < oldList.size(); i++) {
            FieldKey oldKey = oldList.get(i);
            NormalizedField oldF = oldFields.get(oldKey);
            for (int j = 0; j < newList.size(); j++) {
                FieldKey newKey = newList.get(j);
                NormalizedField newF = newFields.get(newKey);

                // base score (reuse existing logic)
                double typeScore = oldF.typeDescriptor.equals(newF.typeDescriptor) ? 1.0 : 0.0;
                double modifierScore = 0.0;
                if (oldF.isStatic == newF.isStatic) modifierScore += 0.5;
                if (oldF.isFinal == newF.isFinal) modifierScore += 0.5;
                double neighborhoodScore = (FieldMatcher.neighborhoodAgreement(oldF.readers, newF.readers, methodMapping)
                        + FieldMatcher.neighborhoodAgreement(oldF.writers, newF.writers, methodMapping)) / 2.0;

                double base = 0.0;
                base += typeScore * 0.5;
                base += (modifierScore / 2.0) * 0.1;
                base += neighborhoodScore * neighborWeight;

                // class-owner soft boost
                double classAlign = 0.0;
                ClassMatcher.ClassMatch classMatch = classMatchByOldOwner.get(oldKey.owner);
                if (classMatch != null) {
                    String expectedNewClass = classMatch.newFp.internalName;
                    if (newKey.owner.equals(expectedNewClass)) {
                        classAlign = 1.0;
                    }
                }
                double finalScore = base * (1.0 - classOwnerWeight) + classAlign * classOwnerWeight;

                // Discard too-low scores by leaving as zero (still participates in Hungarian but will be filtered later)
                if (finalScore >= minScoreThreshold) {
                    weight[i][j] = finalScore;
                    maxWeight = Math.max(maxWeight, finalScore);
                } else {
                    weight[i][j] = 0.0;
                }
            }
        }

        // Pad remainder rows/cols with zeros implicitly (weight initialized to 0)

        // Hungarian solves minimization; to maximize we convert: cost = maxWeight - weight
        double[][] costMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                costMatrix[i][j] = maxWeight - weight[i][j];
            }
        }

        int[] assignment = new HungarianAlgorithm(costMatrix).execute(); // assignment[i] = column matched to row i

        List<Match> result = new ArrayList<>();
        for (int i = 0; i < oldList.size(); i++) {
            int j = assignment[i];
            if (j >= 0 && j < newList.size()) {
                double sc = weight[i][j];
                if (sc >= minScoreThreshold) {
                    result.add(new Match(oldList.get(i), newList.get(j), sc));
                }
            }
        }

        result.sort(Comparator.comparingDouble((Match m) -> -m.score));
        return result;
    }
}
