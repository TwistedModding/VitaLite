package com.tonic.remapper.classes;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class matcher that uses type usage evidence and iterative refinement
 */
public class TypeEvidenceClassMatcher {

    public static List<ClassMatch> matchClassesTopK(
            Collection<ClassNode> oldClasses,
            Collection<ClassNode> newClasses,
            int topK,
            double minSimilarity) {

        System.out.println("=== Starting Type Evidence Class Matching ===");

        // Build enhanced fingerprints
        Map<String, ClassFingerprint> oldFingerprints =
                ClassFingerprint.buildFingerprintsWithReferences(oldClasses);
        Map<String, ClassFingerprint> newFingerprints =
                ClassFingerprint.buildFingerprintsWithReferences(newClasses);

        // RULE 1: Separate obfuscated (2-letter) and non-obfuscated (>2 letter) classes
        Map<String, ClassFingerprint> oldObfuscated = new HashMap<>();
        Map<String, ClassFingerprint> oldNonObfuscated = new HashMap<>();
        Map<String, ClassFingerprint> newObfuscated = new HashMap<>();
        Map<String, ClassFingerprint> newNonObfuscated = new HashMap<>();

        for (Map.Entry<String, ClassFingerprint> entry : oldFingerprints.entrySet()) {
            String name = entry.getKey();
            if (isObfuscatedName(name)) {
                oldObfuscated.put(name, entry.getValue());
            } else {
                oldNonObfuscated.put(name, entry.getValue());
            }
        }

        for (Map.Entry<String, ClassFingerprint> entry : newFingerprints.entrySet()) {
            String name = entry.getKey();
            if (isObfuscatedName(name)) {
                newObfuscated.put(name, entry.getValue());
            } else {
                newNonObfuscated.put(name, entry.getValue());
            }
        }

        System.out.println("Old classes: " + oldObfuscated.size() + " obfuscated, " +
                oldNonObfuscated.size() + " non-obfuscated");
        System.out.println("New classes: " + newObfuscated.size() + " obfuscated, " +
                newNonObfuscated.size() + " non-obfuscated");

        // RULE 2: Non-obfuscated classes match 1:1 by name
        Map<String, String> fixedMatches = new HashMap<>();
        for (String oldName : oldNonObfuscated.keySet()) {
            if (newNonObfuscated.containsKey(oldName)) {
                fixedMatches.put(oldName, oldName);
                System.out.println("Fixed 1:1 match: " + oldName);
            } else {
                System.out.println("WARNING: Non-obfuscated class " + oldName + " has no match in new version");
            }
        }

        // Build type usage evidence (only for obfuscated classes)
        TypeUsageEvidence oldEvidence = buildTypeUsageEvidence(new ArrayList<>(oldClasses));
        TypeUsageEvidence newEvidence = buildTypeUsageEvidence(new ArrayList<>(newClasses));

        // Identify constant/enum-like classes (high self-reference count) among obfuscated only
        Set<String> oldConstantClasses = new HashSet<>();
        Set<String> newConstantClasses = new HashSet<>();

        for (String className : oldObfuscated.keySet()) {
            int selfRefs = oldEvidence.selfFieldReferences.getOrDefault(className, 0);
            if (selfRefs > 50) {
                oldConstantClasses.add(className);
                System.out.println("Detected old constant class: " + className +
                        " with " + selfRefs + " self-refs");
            }
        }

        for (String className : newObfuscated.keySet()) {
            int selfRefs = newEvidence.selfFieldReferences.getOrDefault(className, 0);
            if (selfRefs > 50) {
                newConstantClasses.add(className);
                System.out.println("Detected new constant class: " + className +
                        " with " + selfRefs + " self-refs");
            }
        }

        // Get the best mapping for obfuscated classes only
        Map<String, String> obfuscatedMapping = findBestMapping(
                oldObfuscated, newObfuscated, oldEvidence, newEvidence,
                minSimilarity, fixedMatches);

        // Combine fixed matches with obfuscated matches
        Map<String, String> combinedMapping = new HashMap<>(fixedMatches);
        combinedMapping.putAll(obfuscatedMapping);

        // Additional validation pass for constant classes
        for (String oldConstant : oldConstantClasses) {
            String currentMatch = combinedMapping.get(oldConstant);
            if (currentMatch == null) continue;

            // Verify this is the best match by checking external usage
            Set<String> oldExternalUsers = new HashSet<>(
                    oldEvidence.usedAsFieldType.getOrDefault(oldConstant, Set.of()));
            oldExternalUsers.remove(oldConstant);

            if (!oldExternalUsers.isEmpty()) {
                // Find the best constant class match
                String bestConstantMatch = null;
                double bestScore = 0.0;

                for (String newConstant : newConstantClasses) {
                    // Skip if already used by another class
                    if (!newConstant.equals(currentMatch) &&
                            combinedMapping.containsValue(newConstant)) continue;

                    Set<String> newExternalUsers = new HashSet<>(
                            newEvidence.usedAsFieldType.getOrDefault(newConstant, Set.of()));
                    newExternalUsers.remove(newConstant);

                    // Check if external users map correctly
                    int matches = 0;
                    for (String oldUser : oldExternalUsers) {
                        String mappedUser = combinedMapping.get(oldUser);
                        if (mappedUser != null && newExternalUsers.contains(mappedUser)) {
                            matches++;
                        }
                    }

                    // Also consider field count similarity
                    ClassFingerprint oldFp = oldFingerprints.get(oldConstant);
                    ClassFingerprint newFp = newFingerprints.get(newConstant);
                    double fieldSim = 1.0 - Math.abs(oldFp.totalFieldCount - newFp.totalFieldCount) /
                            (double) Math.max(oldFp.totalFieldCount, newFp.totalFieldCount);

                    double score = (matches / (double) Math.max(1, oldExternalUsers.size())) * 0.7 + fieldSim * 0.3;

                    if (score > bestScore) {
                        bestScore = score;
                        bestConstantMatch = newConstant;
                    }
                }

                if (bestConstantMatch != null && !bestConstantMatch.equals(currentMatch) && bestScore > 0.7) {
                    System.out.println("Correcting constant class mapping: " + oldConstant +
                            " from " + currentMatch + " to " + bestConstantMatch);
                    combinedMapping.put(oldConstant, bestConstantMatch);
                }
            }
        }

        // Validate the mapping (but preserve fixed matches)
        Map<String, String> validated = validateClassMapping(
                combinedMapping, oldFingerprints, newFingerprints, fixedMatches);

        // Convert to ClassMatch format
        List<ClassMatch> results = new ArrayList<>();

        if (topK == 1) {
            // For topK=1, use the validated mapping directly
            for (Map.Entry<String, String> entry : validated.entrySet()) {
                String oldClass = entry.getKey();
                String newClass = entry.getValue();

                ClassFingerprint oldFp = oldFingerprints.get(oldClass);
                ClassFingerprint newFp = newFingerprints.get(newClass);

                if (oldFp != null && newFp != null) {
                    // Calculate the enhanced similarity for reporting
                    double baseSimilarity = oldFp.similarity(newFp);
                    double enhancedSimilarity = enhanceWithTypeEvidence(
                            oldClass, newClass, baseSimilarity,
                            oldEvidence, newEvidence, validated,
                            oldFingerprints, newFingerprints);

                    ClassMatch match = new EnhancedClassMatch(oldFp, newFp, enhancedSimilarity);
                    results.add(match);
                }
            }

            System.out.println("\n=== Final Class Matching Results (topK=1) ===");
            System.out.println("Total matches: " + results.size());

//            results.stream()
//                    .filter(m -> m.oldFp.internalName.length() <= 2)
//                    .limit(10)
//                    .forEach(m -> System.out.println("  " + m.oldFp.internalName + " -> " +
//                            m.newFp.internalName + " (sim: " +
//                            String.format("%.3f", m.similarity) + ")"));

        } else {
            // For topK > 1, handle accordingly (keeping name rules in mind)
            throw new UnsupportedOperationException("topK > 1 not fully implemented with name rules");
        }

        // Sort results by old class name for consistent ordering
        results.sort(Comparator.comparing(cm -> cm.oldFp.internalName));

        return results;
    }

    /**
     * Check if a class name is obfuscated (2 letters or less)
     */
    private static boolean isObfuscatedName(String name) {
        // Handle internal names that might have packages
        String simpleName = name.contains("/") ?
                name.substring(name.lastIndexOf('/') + 1) : name;
        return simpleName.length() <= 2;
    }

    /**
     * Validate class mapping while preserving fixed matches
     */
    private static Map<String, String> validateClassMapping(
            Map<String, String> mapping,
            Map<String, ClassFingerprint> oldFingerprints,
            Map<String, ClassFingerprint> newFingerprints,
            Map<String, String> fixedMatches) {

        Map<String, String> validated = new HashMap<>(mapping);

        // Preserve all fixed matches
        validated.putAll(fixedMatches);

        // Only validate obfuscated class mappings
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 5) {
            changed = false;
            iterations++;

            for (Map.Entry<String, String> entry : new HashMap<>(validated).entrySet()) {
                String oldClass = entry.getKey();
                String newClass = entry.getValue();

                // Skip fixed matches
                if (fixedMatches.containsKey(oldClass)) continue;

                // Skip if not obfuscated
                if (!isObfuscatedName(oldClass)) continue;

                ClassFingerprint oldFp = oldFingerprints.get(oldClass);
                ClassFingerprint newFp = newFingerprints.get(newClass);

                if (oldFp == null || newFp == null) continue;

                // Check type consistency
                int matches = 0;
                int mismatches = 0;

                for (String oldRef : oldFp.referencedTypes) {
                    String mappedRef = validated.get(oldRef);
                    if (mappedRef != null) {
                        if (newFp.referencedTypes.contains(mappedRef)) {
                            matches++;
                        } else {
                            mismatches++;
                        }
                    }
                }

                // If too many mismatches, try to find a better match
                double mismatchRatio = mismatches / (double) Math.max(1, matches + mismatches);

                if (mismatchRatio > 0.5 && mismatches > 2) {
                    // Look for a better match among unmapped obfuscated classes
                    String betterMatch = null;
                    double bestScore = oldFp.similarity(newFp);

                    Set<String> usedNewClasses = new HashSet<>(validated.values());

                    for (Map.Entry<String, ClassFingerprint> newEntry : newFingerprints.entrySet()) {
                        String candidateClass = newEntry.getKey();

                        // Must be obfuscated and unused
                        if (!isObfuscatedName(candidateClass)) continue;
                        if (usedNewClasses.contains(candidateClass)) continue;

                        ClassFingerprint candidateFp = newEntry.getValue();

                        double score = oldFp.similarity(candidateFp);
                        if (score > bestScore) {
                            // Additional validation
                            int newMatches = 0;
                            for (String oldRef : oldFp.referencedTypes) {
                                String mappedRef = validated.get(oldRef);
                                if (mappedRef != null && candidateFp.referencedTypes.contains(mappedRef)) {
                                    newMatches++;
                                }
                            }

                            if (newMatches > matches) {
                                betterMatch = candidateClass;
                                bestScore = score;
                            }
                        }
                    }

                    if (betterMatch != null) {
                        validated.put(oldClass, betterMatch);
                        changed = true;
                        //System.out.println("Validation: Remapped " + oldClass + " from " +
                        //        newClass + " to " + betterMatch);
                    }
                }
            }
        }

        return validated;
    }

    /**
     * Find the best mapping for obfuscated classes
     */
    private static Map<String, String> findBestMapping(
            Map<String, ClassFingerprint> oldFps,
            Map<String, ClassFingerprint> newFps,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            double minSimilarity,
            Map<String, String> fixedMatches) {

        // 0. Match constant classes first
        Map<String, String> constantMatches = matchConstantClassesFirst(
                oldFps, newFps, oldEvidence, newEvidence, minSimilarity);
        System.out.println("Pre-matched " + constantMatches.size() + " constant classes");

        // 1. Find anchor classes - start with constant matches
        Map<String, String> anchorMatches = new HashMap<>(constantMatches);
        Map<String, String> additionalAnchors = findAnchorClasses(
                oldFps, newFps, oldEvidence, newEvidence, minSimilarity,
                anchorMatches, fixedMatches);
        anchorMatches.putAll(additionalAnchors);
        System.out.println("Found " + anchorMatches.size() + " total anchor classes");

        // 2. Use type propagation from anchors (including fixed matches for context)
        Map<String, String> contextMapping = new HashMap<>(anchorMatches);
        contextMapping.putAll(fixedMatches);

        Map<String, String> propagatedMatches = propagateFromAnchors(
                contextMapping, oldEvidence, newEvidence, oldFps, newFps, minSimilarity);
        System.out.println("Propagated to " + propagatedMatches.size() + " total matches");

        // 3. Light refinement (less aggressive to avoid breaking good matches)
        Map<String, String> refined = lightRefinement(
                propagatedMatches, oldEvidence, newEvidence, oldFps, newFps,
                minSimilarity, fixedMatches);

        // 4. Final validation with type consistency
        Map<String, String> validated = validateWithTypeConsistency(
                refined, oldEvidence, newEvidence, fixedMatches);

        return validated;
    }

    /**
     * Match constant/enum-like classes first
     */
    private static Map<String, String> matchConstantClassesFirst(
            Map<String, ClassFingerprint> oldFps,
            Map<String, ClassFingerprint> newFps,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            double minSimilarity) {

        Map<String, String> constantMatches = new HashMap<>();
        Set<String> usedNewClasses = new HashSet<>();

        // Find all constant-like classes (obfuscated only)
        List<String> oldConstants = oldFps.keySet().stream()
                .filter(c -> isObfuscatedName(c))
                .filter(c -> oldEvidence.selfFieldReferences.getOrDefault(c, 0) > 50)
                .sorted((a, b) -> {
                    // Sort by external usage count
                    Set<String> aUsers = new HashSet<>(oldEvidence.usedAsFieldType.getOrDefault(a, Set.of()));
                    aUsers.remove(a);
                    Set<String> bUsers = new HashSet<>(oldEvidence.usedAsFieldType.getOrDefault(b, Set.of()));
                    bUsers.remove(b);
                    return Integer.compare(aUsers.size(), bUsers.size());
                })
                .collect(Collectors.toList());

        List<String> newConstants = newFps.keySet().stream()
                .filter(c -> isObfuscatedName(c))
                .filter(c -> newEvidence.selfFieldReferences.getOrDefault(c, 0) > 50)
                .collect(Collectors.toList());

        System.out.println("Matching " + oldConstants.size() + " old constant classes to " +
                newConstants.size() + " new constant classes");

        // Match constants with unique patterns first
        for (String oldConstant : oldConstants) {
            ClassFingerprint oldFp = oldFps.get(oldConstant);
            Set<String> oldExternalUsers = new HashSet<>(
                    oldEvidence.usedAsFieldType.getOrDefault(oldConstant, Set.of()));
            oldExternalUsers.remove(oldConstant);

            String bestMatch = null;
            double bestScore = minSimilarity;

            for (String newConstant : newConstants) {
                if (usedNewClasses.contains(newConstant)) continue;

                ClassFingerprint newFp = newFps.get(newConstant);
                Set<String> newExternalUsers = new HashSet<>(
                        newEvidence.usedAsFieldType.getOrDefault(newConstant, Set.of()));
                newExternalUsers.remove(newConstant);

                double score = 0.0;
                double weight = 0.0;

                // External user count similarity
                if (oldExternalUsers.size() == newExternalUsers.size() && oldExternalUsers.size() > 0) {
                    score += 1.0 * 5.0;
                    weight += 5.0;
                } else if (Math.abs(oldExternalUsers.size() - newExternalUsers.size()) <= 1) {
                    score += 0.8 * 5.0;
                    weight += 5.0;
                } else {
                    score += 0.0;
                    weight += 5.0;
                }

                // Field count similarity (very important for constants)
                double fieldSim = 1.0 - Math.abs(oldFp.totalFieldCount - newFp.totalFieldCount) /
                        (double) Math.max(oldFp.totalFieldCount, newFp.totalFieldCount);
                score += fieldSim * 4.0;
                weight += 4.0;

                // Method count similarity
                double methodSim = 1.0 - Math.abs(oldFp.totalMethodCount - newFp.totalMethodCount) /
                        (double) Math.max(Math.max(1, oldFp.totalMethodCount), newFp.totalMethodCount);
                score += methodSim * 2.0;
                weight += 2.0;

                // Basic fingerprint
                score += oldFp.similarity(newFp) * 1.0;
                weight += 1.0;

                double totalScore = score / weight;

                if (totalScore > bestScore) {
                    bestScore = totalScore;
                    bestMatch = newConstant;
                }
            }

            if (bestMatch != null) {
                constantMatches.put(oldConstant, bestMatch);
                usedNewClasses.add(bestMatch);
                System.out.println("Matched constant class: " + oldConstant + " -> " + bestMatch +
                        " (score: " + String.format("%.3f", bestScore) + ")");
            }
        }

        return constantMatches;
    }

    /**
     * Find anchor classes (high confidence matches)
     */
    private static Map<String, String> findAnchorClasses(
            Map<String, ClassFingerprint> oldFps,
            Map<String, ClassFingerprint> newFps,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            double minSimilarity,
            Map<String, String> existingMatches,
            Map<String, String> fixedMatches) {

        Map<String, String> anchors = new HashMap<>();
        Set<String> usedNewClasses = new HashSet<>(existingMatches.values());
        usedNewClasses.addAll(fixedMatches.values());

        // Priority: Classes with unique characteristics (obfuscated only)
        List<String> prioritizedOldClasses = oldFps.keySet().stream()
                .filter(c -> isObfuscatedName(c))
                .filter(c -> !existingMatches.containsKey(c))
                .sorted((a, b) -> {
                    ClassFingerprint fpA = oldFps.get(a);
                    ClassFingerprint fpB = oldFps.get(b);

                    // Prioritize by external usage
                    int externalUsageA = oldEvidence.externalFieldReferences.getOrDefault(a, 0) +
                            oldEvidence.externalParamReferences.getOrDefault(a, 0);
                    int externalUsageB = oldEvidence.externalFieldReferences.getOrDefault(b, 0) +
                            oldEvidence.externalParamReferences.getOrDefault(b, 0);

                    int scoreA = externalUsageA * 10 + fpA.runeLiteEventSubscriptions.size() * 20 +
                            fpA.externalMethodCalls.size();
                    int scoreB = externalUsageB * 10 + fpB.runeLiteEventSubscriptions.size() * 20 +
                            fpB.externalMethodCalls.size();

                    return Integer.compare(scoreB, scoreA);
                })
                .collect(Collectors.toList());

        for (String oldClass : prioritizedOldClasses) {
            ClassFingerprint oldFp = oldFps.get(oldClass);

            String bestMatch = null;
            double bestScore = minSimilarity;

            for (Map.Entry<String, ClassFingerprint> newEntry : newFps.entrySet()) {
                String newClass = newEntry.getKey();

                // Must be obfuscated and unused
                if (!isObfuscatedName(newClass)) continue;
                if (usedNewClasses.contains(newClass)) continue;

                ClassFingerprint newFp = newEntry.getValue();

                double score = calculateAnchorScore(
                        oldClass, newClass, oldFp, newFp, oldEvidence, newEvidence);

                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = newClass;
                }
            }

            // Higher threshold for anchors
            if (bestMatch != null && bestScore > Math.max(0.75, minSimilarity + 0.25)) {
                anchors.put(oldClass, bestMatch);
                usedNewClasses.add(bestMatch);
            }
        }

        return anchors;
    }

    /**
     * Calculate score for potential anchor matches
     */
    private static double calculateAnchorScore(
            String oldClass, String newClass,
            ClassFingerprint oldFp, ClassFingerprint newFp,
            TypeUsageEvidence oldEvidence, TypeUsageEvidence newEvidence) {

        double score = 0.0;
        double weight = 0.0;

        // Basic fingerprint similarity (increase weight)
        double fpSim = oldFp.similarity(newFp);
        score += fpSim * 2.0;  // Increased from 1.0
        weight += 2.0;

        // NEW: Check field type usage patterns - CRITICAL for correct matching
        Set<String> oldFieldTypes = oldEvidence.fieldTypeUsages.getOrDefault(oldClass, Set.of());
        Set<String> newFieldTypes = newEvidence.fieldTypeUsages.getOrDefault(newClass, Set.of());

        // If one class uses many field types and the other uses none, huge penalty
        if (oldFieldTypes.size() > 3 && newFieldTypes.isEmpty()) {
            // This is likely NOT a match
            score += 0.0;  // Add nothing
            weight += 10.0; // But add significant weight to dilute other scores
        } else if (!oldFieldTypes.isEmpty() || !newFieldTypes.isEmpty()) {
            // Compare field type usage patterns
            double fieldTypeRatio = 1.0 - Math.abs(oldFieldTypes.size() - newFieldTypes.size()) /
                    (double) Math.max(Math.max(1, oldFieldTypes.size()), newFieldTypes.size());
            score += fieldTypeRatio * 5.0;
            weight += 5.0;
        }

        // Field count similarity for large classes
        if (oldFp.totalFieldCount > 10 && newFp.totalFieldCount > 10) {
            double fieldCountSim = 1.0 - Math.abs(oldFp.totalFieldCount - newFp.totalFieldCount) /
                    (double) Math.max(oldFp.totalFieldCount, newFp.totalFieldCount);
            score += fieldCountSim * 4.0;  // Increased from 3.0
            weight += 4.0;
        }

        // External field usage - but check actual patterns, not just count
        Set<String> oldExternalFieldUsers = new HashSet<>(
                oldEvidence.usedAsFieldType.getOrDefault(oldClass, Set.of()));
        oldExternalFieldUsers.remove(oldClass);

        Set<String> newExternalFieldUsers = new HashSet<>(
                newEvidence.usedAsFieldType.getOrDefault(newClass, Set.of()));
        newExternalFieldUsers.remove(newClass);

        if (!oldExternalFieldUsers.isEmpty() && !newExternalFieldUsers.isEmpty()) {
            double userCountSim = 1.0 - Math.abs(oldExternalFieldUsers.size() - newExternalFieldUsers.size()) /
                    (double) Math.max(Math.max(1, oldExternalFieldUsers.size()), newExternalFieldUsers.size());

            // Reduce weight when counts are identical (no differentiation)
            if (oldExternalFieldUsers.size() == newExternalFieldUsers.size()) {
                score += userCountSim * 2.0;  // Reduced from 8.0
                weight += 2.0;
            } else if (oldExternalFieldUsers.size() <= 3) {
                score += userCountSim * 6.0;  // Still high for unique patterns
                weight += 6.0;
            } else {
                score += userCountSim * 3.0;
                weight += 3.0;
            }
        }

        // Self-reference pattern (keep low weight)
        int oldSelfRefs = oldEvidence.selfFieldReferences.getOrDefault(oldClass, 0);
        int newSelfRefs = newEvidence.selfFieldReferences.getOrDefault(newClass, 0);

        if (oldSelfRefs > 50 && newSelfRefs > 50) {
            double selfRefRatio = Math.min(oldSelfRefs, newSelfRefs) /
                    (double) Math.max(oldSelfRefs, newSelfRefs);
            score += selfRefRatio * 0.3;
            weight += 0.3;
        }

        // Method count similarity - REDUCE weight due to static method shuffling
        double methodCountSim = 1.0 - Math.abs(oldFp.totalMethodCount - newFp.totalMethodCount) /
                (double) Math.max(Math.max(1, oldFp.totalMethodCount), newFp.totalMethodCount);

        // Apply a more lenient threshold for method count differences
        if (Math.abs(oldFp.totalMethodCount - newFp.totalMethodCount) <= 10) {
            // Within 10 methods is considered reasonable due to static shuffling
            methodCountSim = Math.max(methodCountSim, 0.7);
        }

        score += methodCountSim * 1.0;  // Reduced from 2.0
        weight += 1.0;

        // RuneLite event subscription exact match (keep high value)
        if (!oldFp.runeLiteEventSubscriptions.isEmpty() &&
                oldFp.runeLiteEventSubscriptions.equals(newFp.runeLiteEventSubscriptions)) {
            score += 5.0;
            weight += 5.0;
        }

        return weight > 0 ? score / weight : 0.0;
    }

    /**
     * Propagate matches from anchor classes using type evidence
     */
    private static Map<String, String> propagateFromAnchors(
            Map<String, String> anchors,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            Map<String, ClassFingerprint> oldFps,
            Map<String, ClassFingerprint> newFps,
            double minSimilarity) {

        Map<String, String> propagated = new HashMap<>(anchors);
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 5) { // Reduced iterations
            changed = false;
            iterations++;

            for (String oldClass : oldFps.keySet()) {
                if (propagated.containsKey(oldClass)) continue;
                if (!isObfuscatedName(oldClass)) continue; // Only propagate obfuscated classes

                // Collect evidence from mapped classes
                Map<String, Double> evidence = new HashMap<>();

                // Evidence from fields
                Set<String> oldUsers = oldEvidence.usedAsFieldType.getOrDefault(oldClass, Set.of());
                for (String oldUser : oldUsers) {
                    if (oldUser.equals(oldClass)) continue;

                    String mappedUser = propagated.get(oldUser);
                    if (mappedUser != null) {
                        Set<String> newTypes = newEvidence.fieldTypeUsages.getOrDefault(mappedUser, Set.of());
                        for (String newType : newTypes) {
                            if (!propagated.containsValue(newType) && isObfuscatedName(newType)) {
                                evidence.merge(newType, 3.0, Double::sum);
                            }
                        }
                    }
                }

                // Evidence from method parameters
                Set<String> oldParamUsers = oldEvidence.usedAsParamType.getOrDefault(oldClass, Set.of());
                for (String oldUser : oldParamUsers) {
                    if (oldUser.equals(oldClass)) continue;

                    String mappedUser = propagated.get(oldUser);
                    if (mappedUser != null) {
                        Set<String> newTypes = newEvidence.paramTypeUsages.getOrDefault(mappedUser, Set.of());
                        for (String newType : newTypes) {
                            if (!propagated.containsValue(newType) && isObfuscatedName(newType)) {
                                evidence.merge(newType, 2.0, Double::sum);
                            }
                        }
                    }
                }

                // Find best match based on evidence
                String bestMatch = null;
                double bestScore = 0.0;

                for (Map.Entry<String, Double> entry : evidence.entrySet()) {
                    String newClass = entry.getKey();
                    double evidenceScore = entry.getValue();

                    double totalWeight = (oldUsers.size() - 1) * 3.0 +
                            (oldParamUsers.size() - 1) * 2.0;

                    double normalizedEvidence = totalWeight > 0 ? evidenceScore / totalWeight : 0.0;

                    ClassFingerprint oldFp = oldFps.get(oldClass);
                    ClassFingerprint newFp = newFps.get(newClass);

                    if (oldFp != null && newFp != null) {
                        double fpSim = oldFp.similarity(newFp);
                        double combined = normalizedEvidence * 0.6 + fpSim * 0.4; // Adjusted weights

                        if (combined > bestScore && combined > minSimilarity) {
                            bestScore = combined;
                            bestMatch = newClass;
                        }
                    }
                }

                if (bestMatch != null && bestScore > minSimilarity + 0.1) { // Higher threshold
                    propagated.put(oldClass, bestMatch);
                    changed = true;
                    //System.out.println("Propagated: " + oldClass + " -> " + bestMatch +
                    //        " (score: " + String.format("%.3f", bestScore) + ")");
                }
            }
        }

        return propagated;
    }

    /**
     * Light refinement - less aggressive to avoid breaking good matches
     */
    private static Map<String, String> lightRefinement(
            Map<String, String> initial,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            Map<String, ClassFingerprint> oldFps,
            Map<String, ClassFingerprint> newFps,
            double minSimilarity,
            Map<String, String> fixedMatches) {

        Map<String, String> current = new HashMap<>(initial);

        // Only do 2 rounds of light refinement
        for (int round = 0; round < 2; round++) {
            Map<String, Double> confidences = evaluateMappingConfidence(
                    current, oldEvidence, newEvidence);

            // Only refine very low confidence mappings
            List<String> veryLowConfidence = confidences.entrySet().stream()
                    .filter(e -> !fixedMatches.containsKey(e.getKey()))
                    .filter(e -> isObfuscatedName(e.getKey()))
                    .filter(e -> e.getValue() < 0.3) // Only very low confidence
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (veryLowConfidence.isEmpty()) break;

            boolean improved = false;
            for (String oldClass : veryLowConfidence) {
                String currentMatch = current.get(oldClass);
                String betterMatch = findBetterMatch(
                        oldClass, current, oldEvidence, newEvidence, oldFps, newFps,
                        minSimilarity + 0.2); // Higher threshold for changes

                if (betterMatch != null && !betterMatch.equals(currentMatch)) {
                    current.put(oldClass, betterMatch);
                    improved = true;
                    //System.out.println("Refined: " + oldClass + " from " +
                    //        currentMatch + " to " + betterMatch);
                }
            }

            if (!improved) break;
        }

        return current;
    }

    /**
     * Evaluate confidence of each mapping based on type consistency
     */
    private static Map<String, Double> evaluateMappingConfidence(
            Map<String, String> mapping,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence) {

        Map<String, Double> confidences = new HashMap<>();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String oldClass = entry.getKey();
            String newClass = entry.getValue();

            double weightedScore = 0.0;
            double totalWeight = 0.0;

            // Check field type consistency
            Set<String> oldFieldTypes = oldEvidence.fieldTypeUsages.getOrDefault(oldClass, Set.of());
            Set<String> newFieldTypes = newEvidence.fieldTypeUsages.getOrDefault(newClass, Set.of());

            for (String oldType : oldFieldTypes) {
                if (oldType.equals(oldClass)) continue;

                String mappedType = mapping.get(oldType);
                if (mappedType != null && newFieldTypes.contains(mappedType)) {
                    weightedScore += 2.0;
                }
                totalWeight += 2.0;
            }

            // Check which classes use this as a field type
            Set<String> oldFieldUsers = new HashSet<>(oldEvidence.usedAsFieldType.getOrDefault(oldClass, Set.of()));
            oldFieldUsers.remove(oldClass);
            Set<String> newFieldUsers = new HashSet<>(newEvidence.usedAsFieldType.getOrDefault(newClass, Set.of()));
            newFieldUsers.remove(newClass);

            for (String oldUser : oldFieldUsers) {
                String mappedUser = mapping.get(oldUser);
                if (mappedUser != null && newFieldUsers.contains(mappedUser)) {
                    weightedScore += 3.0;
                }
                totalWeight += 3.0;
            }

            double confidence = totalWeight > 0 ? weightedScore / totalWeight : 0.5;
            confidences.put(oldClass, confidence);
        }

        return confidences;
    }

    /**
     * Find a better match for a class with low confidence
     */
    private static String findBetterMatch(
            String oldClass,
            Map<String, String> currentMapping,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            Map<String, ClassFingerprint> oldFps,
            Map<String, ClassFingerprint> newFps,
            double minSimilarity) {

        Set<String> usedNewClasses = new HashSet<>(currentMapping.values());
        ClassFingerprint oldFp = oldFps.get(oldClass);
        if (oldFp == null) return null;

        String bestMatch = null;
        double bestScore = minSimilarity;

        for (Map.Entry<String, ClassFingerprint> newEntry : newFps.entrySet()) {
            String newClass = newEntry.getKey();

            // Must be obfuscated and unused
            if (!isObfuscatedName(newClass)) continue;
            if (usedNewClasses.contains(newClass)) continue;

            ClassFingerprint newFp = newEntry.getValue();

            double score = calculateComprehensiveScore(
                    oldClass, newClass, oldFp, newFp,
                    oldEvidence, newEvidence, currentMapping);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = newClass;
            }
        }

        return bestMatch;
    }

    /**
     * Calculate comprehensive score using all available evidence
     */
    private static double calculateComprehensiveScore(
            String oldClass, String newClass,
            ClassFingerprint oldFp, ClassFingerprint newFp,
            TypeUsageEvidence oldEvidence, TypeUsageEvidence newEvidence,
            Map<String, String> currentMapping) {

        // Start with fingerprint similarity
        double score = oldFp.similarity(newFp) * 0.3;

        // For classes with many self-references, focus on external patterns
        int oldSelfRefs = oldEvidence.selfFieldReferences.getOrDefault(oldClass, 0);
        int newSelfRefs = newEvidence.selfFieldReferences.getOrDefault(newClass, 0);

        boolean isConstantClass = oldSelfRefs > 50 || newSelfRefs > 50;

        if (isConstantClass) {
            // For constant classes, external usage and field count matter most
            Set<String> oldExternalUsers = new HashSet<>(
                    oldEvidence.usedAsFieldType.getOrDefault(oldClass, Set.of()));
            oldExternalUsers.remove(oldClass);

            Set<String> newExternalUsers = new HashSet<>(
                    newEvidence.usedAsFieldType.getOrDefault(newClass, Set.of()));
            newExternalUsers.remove(newClass);

            // Check if external users map correctly
            int externalMatches = 0;
            for (String oldUser : oldExternalUsers) {
                String mappedUser = currentMapping.get(oldUser);
                if (mappedUser != null && newExternalUsers.contains(mappedUser)) {
                    externalMatches++;
                }
            }

            if (!oldExternalUsers.isEmpty()) {
                score += (double) externalMatches / oldExternalUsers.size() * 0.4;
            }

            // Field count similarity
            double fieldCountSim = 1.0 - Math.abs(oldFp.totalFieldCount - newFp.totalFieldCount) /
                    (double) Math.max(oldFp.totalFieldCount, newFp.totalFieldCount);
            score += fieldCountSim * 0.3;

        } else {
            // For normal classes, check type references
            Set<String> oldRefs = new HashSet<>();
            oldRefs.addAll(oldEvidence.fieldTypeUsages.getOrDefault(oldClass, Set.of()));
            oldRefs.addAll(oldEvidence.paramTypeUsages.getOrDefault(oldClass, Set.of()));
            oldRefs.remove(oldClass);

            Set<String> newRefs = new HashSet<>();
            newRefs.addAll(newEvidence.fieldTypeUsages.getOrDefault(newClass, Set.of()));
            newRefs.addAll(newEvidence.paramTypeUsages.getOrDefault(newClass, Set.of()));
            newRefs.remove(newClass);

            int matches = 0;
            for (String oldRef : oldRefs) {
                String mappedRef = currentMapping.get(oldRef);
                if (mappedRef != null && newRefs.contains(mappedRef)) {
                    matches++;
                }
            }

            if (!oldRefs.isEmpty()) {
                score += (double) matches / oldRefs.size() * 0.7;
            }
        }

        return score;
    }

    /**
     * Final validation with type consistency
     */
    private static Map<String, String> validateWithTypeConsistency(
            Map<String, String> mapping,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            Map<String, String> fixedMatches) {

        Map<String, String> validated = new HashMap<>(mapping);

        // Preserve fixed matches
        validated.putAll(fixedMatches);

        // Only validate obfuscated mappings
        for (Map.Entry<String, String> entry : new HashMap<>(validated).entrySet()) {
            String oldClass = entry.getKey();
            String newClass = entry.getValue();

            // Skip fixed matches and non-obfuscated classes
            if (fixedMatches.containsKey(oldClass)) continue;
            if (!isObfuscatedName(oldClass)) continue;

            // Get external field users only
            Set<String> oldFieldUsers = new HashSet<>(oldEvidence.usedAsFieldType.getOrDefault(oldClass, Set.of()));
            oldFieldUsers.remove(oldClass);

            // Only check classes with significant external usage
            if (oldFieldUsers.size() > 5) {
                int matchingFields = 0;
                int totalFields = 0;

                for (String oldUser : oldFieldUsers) {
                    String mappedUser = validated.get(oldUser);
                    if (mappedUser != null) {
                        Set<String> newFieldTypes = newEvidence.fieldTypeUsages.getOrDefault(mappedUser, Set.of());
                        totalFields++;
                        if (newFieldTypes.contains(newClass)) {
                            matchingFields++;
                        }
                    }
                }

                // If less than 40% match (more lenient), try to find better
                if (totalFields > 3 && (double) matchingFields / totalFields < 0.4) {
                    System.out.println("WARNING: Suspicious mapping for heavily-used type: " +
                            oldClass + " -> " + newClass +
                            " (only " + matchingFields + "/" + totalFields + " fields match)");
                }
            }
        }

        return validated;
    }

    /**
     * Enhanced similarity scoring
     */
    private static double enhanceWithTypeEvidence(
            String oldClass, String newClass,
            double baseSimilarity,
            TypeUsageEvidence oldEvidence,
            TypeUsageEvidence newEvidence,
            Map<String, String> validatedMapping,
            Map<String, ClassFingerprint> oldFingerprints,
            Map<String, ClassFingerprint> newFingerprints) {

        // Non-obfuscated classes always have perfect similarity if they match
        if (!isObfuscatedName(oldClass) && oldClass.equals(newClass)) {
            return 1.0;
        }

        // Check if this is a constant/enum-like class
        int oldSelfRefs = oldEvidence.selfFieldReferences.getOrDefault(oldClass, 0);
        int newSelfRefs = newEvidence.selfFieldReferences.getOrDefault(newClass, 0);

        boolean isOldConstantClass = oldSelfRefs > 50;
        boolean isNewConstantClass = newSelfRefs > 50;

        // If this is the validated match, boost it
        String validatedMatch = validatedMapping.get(oldClass);
        if (newClass.equals(validatedMatch)) {
            if (isOldConstantClass && isNewConstantClass) {
                ClassFingerprint oldFp = oldFingerprints.get(oldClass);
                ClassFingerprint newFp = newFingerprints.get(newClass);

                double fieldCountSim = 1.0 - Math.abs(oldFp.totalFieldCount - newFp.totalFieldCount) /
                        (double) Math.max(oldFp.totalFieldCount, newFp.totalFieldCount);

                if (fieldCountSim > 0.9) {
                    return Math.min(1.0, baseSimilarity * 1.8);
                }
            }
            return Math.min(1.0, baseSimilarity * 1.5);
        }

        double enhancedScore = baseSimilarity;

        if (isOldConstantClass && isNewConstantClass) {
            // For constant classes, field count similarity matters
            ClassFingerprint oldFp = oldFingerprints.get(oldClass);
            ClassFingerprint newFp = newFingerprints.get(newClass);

            double fieldCountSim = 1.0 - Math.abs(oldFp.totalFieldCount - newFp.totalFieldCount) /
                    (double) Math.max(oldFp.totalFieldCount, newFp.totalFieldCount);

            if (fieldCountSim > 0.9) {
                enhancedScore = Math.min(1.0, enhancedScore + 0.15);
            } else if (fieldCountSim < 0.8) {
                enhancedScore = enhancedScore * 0.9;
            }
        }

        return Math.min(1.0, enhancedScore);
    }

    // Keep all the existing helper classes and methods below...

    /**
     * Enhanced ClassMatch that properly overrides similarity
     */
    static class EnhancedClassMatch extends ClassMatch {
        private final double customSimilarity;

        public EnhancedClassMatch(ClassFingerprint oldFp, ClassFingerprint newFp, double similarity) {
            super(oldFp, newFp);
            this.customSimilarity = similarity;
            this.similarity = similarity; // Override parent field
        }

        @Override
        public String toString() {
            return String.format("ClassMatch{old=%s, new=%s, sim=%.3f}",
                    oldFp.internalName, newFp.internalName, customSimilarity);
        }
    }

    /**
     * Type usage evidence
     */
    static class TypeUsageEvidence {
        Map<String, Set<String>> fieldTypeUsages = new HashMap<>();
        Map<String, Set<String>> paramTypeUsages = new HashMap<>();
        Map<String, Set<String>> returnTypeUsages = new HashMap<>();
        Map<String, Set<String>> usedAsFieldType = new HashMap<>();
        Map<String, Set<String>> usedAsParamType = new HashMap<>();
        Map<String, Set<String>> usedAsReturnType = new HashMap<>();
        Map<String, String> fieldNameToType = new HashMap<>();
        Map<String, Set<String>> typeCoOccurrence = new HashMap<>();
        Map<String, Integer> externalFieldReferences = new HashMap<>();
        Map<String, Integer> selfFieldReferences = new HashMap<>();
        Map<String, Integer> externalParamReferences = new HashMap<>();
        Map<String, Integer> selfParamReferences = new HashMap<>();
    }

    /**
     * Build type usage evidence from classes
     */
    private static TypeUsageEvidence buildTypeUsageEvidence(List<ClassNode> classes) {
        TypeUsageEvidence evidence = new TypeUsageEvidence();

        for (ClassNode cn : classes) {
            Set<String> typesInThisClass = new HashSet<>();

            // Analyze fields
            if (cn.fields != null) {
                for (FieldNode fn : cn.fields) {
                    Type type = Type.getType(fn.desc);
                    if (type.getSort() == Type.OBJECT) {
                        String typeName = type.getInternalName();
                        if (!ClassFingerprint.isStableClass(typeName)) {
                            evidence.fieldTypeUsages
                                    .computeIfAbsent(cn.name, k -> new HashSet<>())
                                    .add(typeName);
                            evidence.usedAsFieldType
                                    .computeIfAbsent(typeName, k -> new HashSet<>())
                                    .add(cn.name);
                            evidence.fieldNameToType.put(fn.name, typeName);
                            typesInThisClass.add(typeName);

                            // Track external vs self references
                            if (typeName.equals(cn.name)) {
                                evidence.selfFieldReferences.merge(typeName, 1, Integer::sum);
                            } else {
                                evidence.externalFieldReferences.merge(typeName, 1, Integer::sum);
                            }
                        }
                    } else if (type.getSort() == Type.ARRAY) {
                        Type elementType = type.getElementType();
                        if (elementType.getSort() == Type.OBJECT) {
                            String typeName = elementType.getInternalName();
                            if (!ClassFingerprint.isStableClass(typeName)) {
                                evidence.fieldTypeUsages
                                        .computeIfAbsent(cn.name, k -> new HashSet<>())
                                        .add(typeName);
                                evidence.usedAsFieldType
                                        .computeIfAbsent(typeName, k -> new HashSet<>())
                                        .add(cn.name);
                                typesInThisClass.add(typeName);

                                if (typeName.equals(cn.name)) {
                                    evidence.selfFieldReferences.merge(typeName, 1, Integer::sum);
                                } else {
                                    evidence.externalFieldReferences.merge(typeName, 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }

            // Analyze methods
            if (cn.methods != null) {
                for (MethodNode mn : cn.methods) {
                    Type methodType = Type.getMethodType(mn.desc);

                    // Parameters
                    for (Type param : methodType.getArgumentTypes()) {
                        if (param.getSort() == Type.OBJECT) {
                            String typeName = param.getInternalName();
                            if (!ClassFingerprint.isStableClass(typeName)) {
                                evidence.paramTypeUsages
                                        .computeIfAbsent(cn.name, k -> new HashSet<>())
                                        .add(typeName);
                                evidence.usedAsParamType
                                        .computeIfAbsent(typeName, k -> new HashSet<>())
                                        .add(cn.name);
                                typesInThisClass.add(typeName);

                                if (typeName.equals(cn.name)) {
                                    evidence.selfParamReferences.merge(typeName, 1, Integer::sum);
                                } else {
                                    evidence.externalParamReferences.merge(typeName, 1, Integer::sum);
                                }
                            }
                        }
                    }

                    // Return type
                    Type returnType = methodType.getReturnType();
                    if (returnType.getSort() == Type.OBJECT) {
                        String typeName = returnType.getInternalName();
                        if (!ClassFingerprint.isStableClass(typeName)) {
                            evidence.returnTypeUsages
                                    .computeIfAbsent(cn.name, k -> new HashSet<>())
                                    .add(typeName);
                            evidence.usedAsReturnType
                                    .computeIfAbsent(typeName, k -> new HashSet<>())
                                    .add(cn.name);
                            typesInThisClass.add(typeName);
                        }
                    }
                }
            }

            // Build co-occurrence
            for (String type1 : typesInThisClass) {
                for (String type2 : typesInThisClass) {
                    if (!type1.equals(type2)) {
                        evidence.typeCoOccurrence
                                .computeIfAbsent(type1, k -> new HashSet<>())
                                .add(type2);
                    }
                }
            }
        }

        return evidence;
    }
}