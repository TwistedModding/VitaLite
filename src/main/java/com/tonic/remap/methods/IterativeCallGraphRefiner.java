package com.tonic.remap.methods;

import java.util.*;

public class IterativeCallGraphRefiner
{
    /**
     * Refines the mapping using neighbor consistency, seeded with an initial mapping.
     *
     * @param candidates        initial top-K matches from MethodMatcher.matchAll
     * @param oldCallGraph      old-method -> its callees
     * @param newCallGraph      new-method -> its callees
     * @param maxRounds         maximum iterations to run
     * @param neighborWeight    weight given to neighborhood agreement [0.0..1.0], rest goes to base score
     * @param initialSeed       initial old->new suggestions to start from (can be empty)
     * @return refined one-to-one mapping old -> new
     */
    public static Map<MethodKey, MethodKey> refine(
            List<MethodMatcher.Match> candidates,
            Map<MethodKey, Set<MethodKey>> oldCallGraph,
            Map<MethodKey, Set<MethodKey>> newCallGraph,
            int maxRounds,
            double neighborWeight,
            Map<MethodKey, MethodKey> initialSeed
    ) {
        if (neighborWeight < 0.0 || neighborWeight > 1.0) {
            throw new IllegalArgumentException("neighborWeight must be in [0,1]");
        }

        // Build inverse graphs for convenience (callers)
        Map<MethodKey, Set<MethodKey>> oldInverse = CallGraphExtractor.invertCallGraph(oldCallGraph);
        Map<MethodKey, Set<MethodKey>> newInverse = CallGraphExtractor.invertCallGraph(newCallGraph);

        // Group candidates per-old and keep sorted by base score descending
        Map<MethodKey, List<MethodMatcher.Match>> candidatesByOld = new HashMap<>();
        for (MethodMatcher.Match m : candidates) {
            candidatesByOld
                    .computeIfAbsent(m.oldKey, k -> new ArrayList<>())
                    .add(m);
        }
        for (List<MethodMatcher.Match> list : candidatesByOld.values()) {
            list.sort(Comparator.comparingDouble((MethodMatcher.Match m) -> -m.score));
        }

        // === initialize mapping using seed, resolving conflicts ===
        Map<MethodKey, MethodKey> mapping = new HashMap<>();
        Map<MethodKey, MethodKey> newToOld = new HashMap<>();
        for (Map.Entry<MethodKey, MethodKey> e : initialSeed.entrySet()) {
            MethodKey old = e.getKey();
            MethodKey seededNew = e.getValue();
            double seededScore = lookupBaseScore(old, seededNew, candidatesByOld);
            if (newToOld.containsKey(seededNew)) {
                MethodKey existingOld = newToOld.get(seededNew);
                double existingScore = lookupBaseScore(existingOld, seededNew, candidatesByOld);
                if (seededScore > existingScore) {
                    // replace with stronger seed
                    mapping.remove(existingOld);
                    newToOld.put(seededNew, old);
                    mapping.put(old, seededNew);
                }
            } else {
                newToOld.put(seededNew, old);
                mapping.put(old, seededNew);
            }
        }

        // Fill in unmapped olds with their best available (avoid duplicates)
        for (Map.Entry<MethodKey, List<MethodMatcher.Match>> e : candidatesByOld.entrySet()) {
            MethodKey old = e.getKey();
            if (mapping.containsKey(old)) continue;
            for (MethodMatcher.Match candidate : e.getValue()) {
                MethodKey newCandidate = candidate.newKey;
                if (!mapping.containsValue(newCandidate)) {
                    mapping.put(old, newCandidate);
                    break;
                }
            }
            // if all top choices are taken, just take the best (allow temporary conflict; will be resolved in iteration)
            if (!mapping.containsKey(old) && !e.getValue().isEmpty()) {
                mapping.put(old, e.getValue().get(0).newKey);
            }
        }

        // Iteratively refine
        for (int round = 0; round < maxRounds; round++) {
            boolean changed = false;

            // Build reverse lookup of current assignments (new -> old)
            Map<MethodKey, MethodKey> currentNewToOld = new HashMap<>();
            for (Map.Entry<MethodKey, MethodKey> e : mapping.entrySet()) {
                currentNewToOld.put(e.getValue(), e.getKey());
            }

            // Compute candidate mapping for this round
            Map<MethodKey, MethodKey> nextMapping = new HashMap<>();
            List<MethodKey> oldKeys = new ArrayList<>(candidatesByOld.keySet());
            // optional: process in descending base-confidence
            oldKeys.sort((a, b) -> {
                double aScore = candidatesByOld.get(a).get(0).score;
                double bScore = candidatesByOld.get(b).get(0).score;
                return Double.compare(bScore, aScore);
            });

            for (MethodKey old : oldKeys) {
                List<MethodMatcher.Match> candList = candidatesByOld.get(old);
                if (candList == null || candList.isEmpty()) continue;

                double bestCombined = -Double.MAX_VALUE;
                MethodKey bestNew = null;

                for (MethodMatcher.Match match : candList) {
                    MethodKey candidateNew = match.newKey;
                    double baseScore = match.score;

                    double neighborScore = computeNeighborAgreement(old, candidateNew, mapping,
                            oldCallGraph, oldInverse, newCallGraph, newInverse);

                    double combined = baseScore * (1.0 - neighborWeight) + neighborScore * neighborWeight;

                    if (combined > bestCombined) {
                        bestCombined = combined;
                        bestNew = candidateNew;
                    }
                }

                if (bestNew != null) {
                    nextMapping.put(old, bestNew);
                }
            }

            // Resolve conflicts to enforce one-to-one: group by new method
            Map<MethodKey, List<MethodAssignment>> byNew = new HashMap<>();
            for (Map.Entry<MethodKey, MethodKey> e : nextMapping.entrySet()) {
                MethodKey old = e.getKey();
                MethodKey newM = e.getValue();
                double baseScore = lookupBaseScore(old, newM, candidatesByOld);
                double neighborScore = computeNeighborAgreement(old, newM, mapping,
                        oldCallGraph, oldInverse, newCallGraph, newInverse);
                double combined = baseScore * (1.0 - neighborWeight) + neighborScore * neighborWeight;
                byNew.computeIfAbsent(newM, k -> new ArrayList<>())
                        .add(new MethodAssignment(old, newM, combined));
            }

            Map<MethodKey, MethodKey> resolved = new HashMap<>();
            Set<MethodKey> takenNew = new HashSet<>();
            // pick the best per new method
            for (Map.Entry<MethodKey, List<MethodAssignment>> e : byNew.entrySet()) {
                List<MethodAssignment> list = e.getValue();
                list.sort(Comparator.comparingDouble((MethodAssignment ma) -> -ma.combinedScore));
                MethodAssignment winner = list.get(0);
                resolved.put(winner.old, winner.newMethod);
                takenNew.add(winner.newMethod);
            }

            // Fallback for olds lost
            for (MethodKey old : oldKeys) {
                if (resolved.containsKey(old)) continue;
                List<MethodMatcher.Match> candList = candidatesByOld.get(old);
                if (candList == null) continue;
                for (MethodMatcher.Match match : candList) {
                    MethodKey candidateNew = match.newKey;
                    if (takenNew.contains(candidateNew)) continue;
                    resolved.put(old, candidateNew);
                    takenNew.add(candidateNew);
                    break;
                }
            }

            // If any mapping changed
            if (!resolved.equals(mapping)) {
                changed = true;
            }

            mapping = resolved;

            if (!changed) {
                break; // converged
            }
        }

        return mapping;
    }

    // backward-compatible overload without seed
    public static Map<MethodKey, MethodKey> refine(
            List<MethodMatcher.Match> candidates,
            Map<MethodKey, Set<MethodKey>> oldCallGraph,
            Map<MethodKey, Set<MethodKey>> newCallGraph,
            int maxRounds,
            double neighborWeight
    ) {
        return refine(candidates, oldCallGraph, newCallGraph, maxRounds, neighborWeight, Collections.emptyMap());
    }

    private static double lookupBaseScore(MethodKey old, MethodKey neW,
                                          Map<MethodKey, List<MethodMatcher.Match>> candidatesByOld) {
        List<MethodMatcher.Match> list = candidatesByOld.get(old);
        if (list != null) {
            for (MethodMatcher.Match m : list) {
                if (m.newKey.equals(neW)) {
                    return m.score;
                }
            }
        }
        return 0.0;
    }

    /**
     * Computes neighbor agreement score in [0,1] between old's mapped neighborhood and candidateNew's neighborhood.
     */
    private static double computeNeighborAgreement(
            MethodKey old,
            MethodKey candidateNew,
            Map<MethodKey, MethodKey> currentMapping,
            Map<MethodKey, Set<MethodKey>> oldCallGraph,
            Map<MethodKey, Set<MethodKey>> oldInverse,
            Map<MethodKey, Set<MethodKey>> newCallGraph,
            Map<MethodKey, Set<MethodKey>> newInverse
    ) {
        // neighbors of old: union of its callers and callees
        Set<MethodKey> oldNeighbors = new HashSet<>();
        oldNeighbors.addAll(oldCallGraph.getOrDefault(old, Collections.emptySet()));
        oldNeighbors.addAll(oldInverse.getOrDefault(old, Collections.emptySet()));
        if (oldNeighbors.isEmpty()) {
            return 0.0;
        }

        // neighbors of candidateNew: union of its callers and callees
        Set<MethodKey> newNeighbors = new HashSet<>();
        newNeighbors.addAll(newCallGraph.getOrDefault(candidateNew, Collections.emptySet()));
        newNeighbors.addAll(newInverse.getOrDefault(candidateNew, Collections.emptySet()));

        int matched = 0;
        int totalConsidered = 0;
        for (MethodKey oldNeighbor : oldNeighbors) {
            MethodKey mapped = currentMapping.get(oldNeighbor);
            if (mapped == null) continue;
            totalConsidered++;
            if (newNeighbors.contains(mapped)) {
                matched++;
            }
        }
        if (totalConsidered == 0) {
            return 0.0;
        }
        return (double) matched / totalConsidered; // simple fraction agreement
    }

    private static class MethodAssignment {
        final MethodKey old;
        final MethodKey newMethod;
        final double combinedScore;

        MethodAssignment(MethodKey old, MethodKey newMethod, double combinedScore) {
            this.old = old;
            this.newMethod = newMethod;
            this.combinedScore = combinedScore;
        }
    }
}
