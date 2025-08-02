package com.tonic.remap;

import java.util.*;
import java.util.stream.Collectors;

public class FieldMatcher {
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
            return String.format("%s -> %s : %.3f", oldKey, newKey, score);
        }
    }

    /**
     * Match all old fields to new fields keeping topKPerOld, using method mapping to compute neighborhood agreement.
     *
     * @param oldFields       normalized old fields
     * @param newFields       normalized new fields
     * @param methodMapping   current method map (oldMethod -> newMethod)
     * @param topKPerOld      how many candidates to keep per old field
     * @param neighborWeight  weight of neighborhood agreement (0..1)
     */
    public static List<Match> matchAll(
            Map<FieldKey, NormalizedField> oldFields,
            Map<FieldKey, NormalizedField> newFields,
            Map<MethodKey, MethodKey> methodMapping,
            int topKPerOld,
            double neighborWeight
    ) {
        if (oldFields.isEmpty() || newFields.isEmpty() || topKPerOld <= 0) {
            return Collections.emptyList();
        }

        long totalPairs = (long) oldFields.size() * (long) newFields.size();
        ProgressBar progressBar = new ProgressBar(totalPairs, 40);
        long processed = 0;

        Map<FieldKey, PriorityQueue<Match>> topKPerOldMap = new LinkedHashMap<>();
        for (Map.Entry<FieldKey, NormalizedField> oEntry : oldFields.entrySet()) {
            FieldKey oldKey = oEntry.getKey();
            PriorityQueue<Match> pq = new PriorityQueue<>(topKPerOld, Comparator.comparingDouble(m -> m.score)); // min-heap
            topKPerOldMap.put(oldKey, pq);

            for (Map.Entry<FieldKey, NormalizedField> nEntry : newFields.entrySet()) {
                FieldKey newKey = nEntry.getKey();
                double s = score(oEntry.getValue(), nEntry.getValue(), methodMapping, neighborWeight);
                Match m = new Match(oldKey, newKey, s);

                if (pq.size() < topKPerOld) {
                    pq.offer(m);
                } else if (s > Objects.requireNonNull(pq.peek()).score) {
                    pq.poll();
                    pq.offer(m);
                }

                processed++;
                if (processed % Math.max(1, totalPairs / 100) == 0 || processed % 1000 == 0 || processed == totalPairs) {
                    progressBar.update(processed);
                }
            }
        }

        List<Match> result = new ArrayList<>();
        for (PriorityQueue<Match> pq : topKPerOldMap.values()) {
            result.addAll(pq);
        }

        result.sort(Comparator.comparingDouble((Match m) -> -m.score));
        progressBar.update(totalPairs); // ensure full bar
        return result;
    }

    private static double score(NormalizedField a,
                                NormalizedField b,
                                Map<MethodKey, MethodKey> methodMap,
                                double neighborWeight) {
        double typeScore = a.typeDescriptor.equals(b.typeDescriptor) ? 1.0 : 0.0; // strong signal
        double modifierScore = 0.0;
        if (a.isStatic == b.isStatic) modifierScore += 0.5;
        if (a.isFinal == b.isFinal) modifierScore += 0.5; // max 1.0 here

        // Neighborhood agreement: readers and writers via method mapping
        double readerAgreement = neighborhoodAgreement(a.readers, b.readers, methodMap);
        double writerAgreement = neighborhoodAgreement(a.writers, b.writers, methodMap);
        double neighborhoodScore = (readerAgreement + writerAgreement) / 2.0; // 0..1

        // Combine: weights can be tuned
        double score = 0.0;
        score += typeScore * 0.5; // type strong
        score += (modifierScore / 2.0) * 0.1; // normalize modifierScore (0..1) then small weight
        score += neighborhoodScore * neighborWeight; // influence from method context

        return score;
    }

    /**
     * Maps old method set through methodMap and compares to newMethodSet.
     * Returns overlap coefficient (intersection / min(sizeMappedOld, sizeNew)) unless both empty -> 1.
     */
    static double neighborhoodAgreement(Set<MethodKey> oldSet,
                                        Set<MethodKey> newSet,
                                        Map<MethodKey, MethodKey> methodMap) {
        if ((oldSet == null || oldSet.isEmpty()) && (newSet == null || newSet.isEmpty())) {
            return 1.0; // no info, treat as neutral positive
        }
        if (oldSet == null || oldSet.isEmpty() || newSet == null || newSet.isEmpty()) {
            return 0.0;
        }

        Set<MethodKey> mappedOld = oldSet.stream()
                .map(methodMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (mappedOld.isEmpty()) {
            return 0.0;
        }

        Set<MethodKey> intersection = new HashSet<>(mappedOld);
        intersection.retainAll(newSet);
        if (intersection.isEmpty()) {
            return 0.0;
        }

        int denom = Math.min(mappedOld.size(), newSet.size());
        if (denom == 0) {
            return 0.0;
        }
        return (double) intersection.size() / denom;
    }
}
