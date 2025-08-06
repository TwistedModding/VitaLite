package com.tonic.remapper.fields;

import com.tonic.remapper.classes.ClassMatch;
import com.tonic.remapper.methods.MethodKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Targeted debugging for specific field matches we know should work
 */
public class TargetedFieldDebug {

    // Expected matches to debug
    private static final List<ExpectedMatch> EXPECTED_MATCHES = Arrays.asList(
            new ExpectedMatch("client", "ct", "client", "cs", "Object field"),
            new ExpectedMatch("mq", "di", "mg", "df", "int field 1"),
            new ExpectedMatch("mq", "df", "mg", "dw", "int field 2")
    );

    private static class ExpectedMatch {
        final String oldClass, oldField, newClass, newField, description;

        ExpectedMatch(String oldClass, String oldField, String newClass, String newField, String desc) {
            this.oldClass = oldClass;
            this.oldField = oldField;
            this.newClass = newClass;
            this.newField = newField;
            this.description = desc;
        }
    }

    public static void debugExpectedMatches(
            Map<FieldKey, FieldNode> oldFields,
            Map<FieldKey, FieldNode> newFields,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey> methodMap,
            Map<String, ClassMatch> classMatchByOldOwner,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> oldProfiles,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> newProfiles) {

        System.out.println("\n========== TARGETED FIELD MATCH DEBUG ==========");

        for (ExpectedMatch expected : EXPECTED_MATCHES) {
            System.out.println("\n--- Debugging " + expected.description + " ---");
            System.out.println("Expected: " + expected.oldClass + "." + expected.oldField +
                    " -> " + expected.newClass + "." + expected.newField);

            // Step 1: Find the actual fields
            FieldKey oldKey = findField(oldFields.keySet(), expected.oldClass, expected.oldField);
            FieldKey newKey = findField(newFields.keySet(), expected.newClass, expected.newField);

            if (oldKey == null) {
                System.out.println("ERROR: Old field not found!");
                listFieldsInClass(oldFields.keySet(), expected.oldClass);
                continue;
            }
            if (newKey == null) {
                System.out.println("ERROR: New field not found!");
                listFieldsInClass(newFields.keySet(), expected.newClass);
                continue;
            }

            System.out.println("Found old field: " + oldKey);
            System.out.println("Found new field: " + newKey);

            // Step 2: Check class mapping
            ClassMatch classMatch = classMatchByOldOwner.get(expected.oldClass);
            if (classMatch == null) {
                System.out.println("ERROR: No class mapping for " + expected.oldClass);
                System.out.println("Available class mappings: " + classMatchByOldOwner.keySet());
            } else {
                System.out.println("Class mapping: " + expected.oldClass + " -> " +
                        classMatch.newFp.internalName + " (score: " + classMatch.similarity + ")");
                if (!classMatch.newFp.internalName.equals(expected.newClass)) {
                    System.out.println("WARNING: Class mapped to " + classMatch.newFp.internalName +
                            " not " + expected.newClass);
                }
            }

            // Step 3: Compute the match score manually
            FieldNode oldFn = oldFields.get(oldKey);
            FieldNode newFn = newFields.get(newKey);

            if (oldFn == null || newFn == null) {
                System.out.println("ERROR: FieldNode not found!");
                continue;
            }

            Map<String, Double> breakdown = computeMatchScore(
                    oldKey, oldFn, newKey, newFn,
                    oldUses, newUses, methodMap, classMatch,
                    oldProfiles != null ? oldProfiles.get(oldKey) : null,
                    newProfiles != null ? newProfiles.get(newKey) : null
            );

            double totalScore = computeWeightedScore(breakdown,
                    (oldFn.access & Opcodes.ACC_STATIC) != 0,
                    isPrimitive(oldKey.desc)
            );

            System.out.println("Score breakdown:");
            breakdown.forEach((component, score) ->
                    System.out.printf("  %s: %.3f\n", component, score));
            System.out.println("TOTAL SCORE: " + totalScore);

            // Step 4: Check method usage details
            Set<MethodKey> oldMethodUse = oldUses.getOrDefault(oldKey, Collections.emptySet());
            Set<MethodKey> newMethodUse = newUses.getOrDefault(newKey, Collections.emptySet());

            System.out.println("Method usage:");
            System.out.println("  Old field used in " + oldMethodUse.size() + " methods");
            if (!oldMethodUse.isEmpty()) {
                System.out.println("  Sample old methods: " +
                        oldMethodUse.stream().limit(3).collect(Collectors.toList()));
            }

            System.out.println("  New field used in " + newMethodUse.size() + " methods");
            if (!newMethodUse.isEmpty()) {
                System.out.println("  Sample new methods: " +
                        newMethodUse.stream().limit(3).collect(Collectors.toList()));
            }

            // Check method mapping
            if (!oldMethodUse.isEmpty()) {
                Set<MethodKey> mappedMethods = oldMethodUse.stream()
                        .map(methodMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                System.out.println("  Mapped methods: " + mappedMethods.size());
                if (!mappedMethods.isEmpty()) {
                    Set<MethodKey> intersection = new HashSet<>(mappedMethods);
                    intersection.retainAll(newMethodUse);
                    System.out.println("  Methods in common: " + intersection.size());
                }
            }

            // Step 5: Find what this field is actually matching with
            System.out.println("\nWhat is this old field matching with?");
            List<ScoredMatch> topMatches = findTopMatchesForField(
                    oldKey, oldFn, newFields, oldUses, newUses, methodMap,
                    classMatchByOldOwner, oldProfiles, newProfiles, 5
            );

            for (int i = 0; i < topMatches.size(); i++) {
                ScoredMatch match = topMatches.get(i);
                System.out.printf("  #%d: %s (score: %.3f)\n", i+1, match.newKey, match.score);
                if (match.newKey.equals(newKey)) {
                    System.out.println("      ^ This is our expected match!");
                }
            }
        }

        System.out.println("\n========== END TARGETED DEBUG ==========\n");
    }

    private static class ScoredMatch {
        final FieldKey newKey;
        final double score;

        ScoredMatch(FieldKey newKey, double score) {
            this.newKey = newKey;
            this.score = score;
        }
    }

    private static List<ScoredMatch> findTopMatchesForField(
            FieldKey oldKey, FieldNode oldFn,
            Map<FieldKey, FieldNode> newFields,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey> methodMap,
            Map<String, ClassMatch> classMatchByOldOwner,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> oldProfiles,
            Map<FieldKey, FieldAccessAnalyzer.FieldAccessProfile> newProfiles,
            int topK) {

        List<ScoredMatch> matches = new ArrayList<>();
        ClassMatch classMatch = classMatchByOldOwner.get(oldKey.owner);

        for (Map.Entry<FieldKey, FieldNode> entry : newFields.entrySet()) {
            FieldKey newKey = entry.getKey();
            FieldNode newFn = entry.getValue();

            // Only consider same type
            if (!newKey.desc.equals(oldKey.desc)) continue;

            Map<String, Double> breakdown = computeMatchScore(
                    oldKey, oldFn, newKey, newFn,
                    oldUses, newUses, methodMap, classMatch,
                    oldProfiles != null ? oldProfiles.get(oldKey) : null,
                    newProfiles != null ? newProfiles.get(newKey) : null
            );

            double score = computeWeightedScore(breakdown,
                    (oldFn.access & Opcodes.ACC_STATIC) != 0,
                    isPrimitive(oldKey.desc)
            );

            matches.add(new ScoredMatch(newKey, score));
        }

        matches.sort((a, b) -> Double.compare(b.score, a.score));
        return matches.subList(0, Math.min(topK, matches.size()));
    }

    private static Map<String, Double> computeMatchScore(
            FieldKey oldKey, FieldNode oldFn,
            FieldKey newKey, FieldNode newFn,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey> methodMap,
            ClassMatch classMatch,
            FieldAccessAnalyzer.FieldAccessProfile oldProfile,
            FieldAccessAnalyzer.FieldAccessProfile newProfile) {

        Map<String, Double> breakdown = new HashMap<>();

        // Type score
        breakdown.put("type", oldKey.desc.equals(newKey.desc) ? 1.0 : 0.0);

        // Modifier score
        boolean oldStatic = (oldFn.access & Opcodes.ACC_STATIC) != 0;
        boolean newStatic = (newFn.access & Opcodes.ACC_STATIC) != 0;
        boolean oldFinal = (oldFn.access & Opcodes.ACC_FINAL) != 0;
        boolean newFinal = (newFn.access & Opcodes.ACC_FINAL) != 0;

        double modifierScore = 0.0;
        if (oldStatic == newStatic) modifierScore += 1.0;
        if (oldFinal == newFinal) modifierScore += 0.5;
        breakdown.put("modifiers", modifierScore / 1.5);

        // Owner score
        double ownerScore = 0.0;
        if (classMatch != null && classMatch.newFp.internalName.equals(newKey.owner)) {
            ownerScore = classMatch.similarity;
            if (!oldStatic && !newStatic) {
                ownerScore *= 1.0;  // Full score for instance fields
            } else {
                ownerScore *= 0.6;  // Reduced for static
            }
        } else if (!oldStatic && !newStatic) {
            ownerScore = -0.2;  // Penalty for instance fields in wrong class
        }
        breakdown.put("owner", ownerScore);

        // Usage score
        Set<MethodKey> oldU = oldUses.getOrDefault(oldKey, Collections.emptySet());
        Set<MethodKey> newU = newUses.getOrDefault(newKey, Collections.emptySet());

        Set<MethodKey> translatedOldU = oldU.stream()
                .map(methodMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        double usageScore = 0.0;
        if (!translatedOldU.isEmpty() && !newU.isEmpty()) {
            Set<MethodKey> intersection = new HashSet<>(translatedOldU);
            intersection.retainAll(newU);
            Set<MethodKey> union = new HashSet<>(translatedOldU);
            union.addAll(newU);
            usageScore = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        }
        breakdown.put("usage", usageScore);

        // Profile score
        double profileScore = 0.0;
        if (oldProfile != null && newProfile != null) {
            profileScore = oldProfile.similarity(newProfile);
        }
        breakdown.put("profile", profileScore);

        // Simplified scores for debugging
        breakdown.put("cooccur", 0.0);
        breakdown.put("proximity", 0.0);
        breakdown.put("pattern", 0.0);

        return breakdown;
    }

    private static double computeWeightedScore(Map<String, Double> breakdown,
                                               boolean isStatic, boolean isPrimitive) {
        double score = 0.0;

        // Use simplified weights for debugging
        score += breakdown.getOrDefault("type", 0.0) * 0.3;
        score += breakdown.getOrDefault("modifiers", 0.0) * 0.1;
        score += breakdown.getOrDefault("owner", 0.0) * 0.3;
        score += breakdown.getOrDefault("usage", 0.0) * 0.3;

        return score;
    }

    private static FieldKey findField(Set<FieldKey> fields, String className, String fieldName) {
        return fields.stream()
                .filter(f -> f.owner.equals(className) && f.name.equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    private static void listFieldsInClass(Set<FieldKey> fields, String className) {
        System.out.println("Fields in class " + className + ":");
        fields.stream()
                .filter(f -> f.owner.equals(className))
                .forEach(f -> System.out.println("  " + f));
    }

    private static boolean isPrimitive(String desc) {
        return desc.length() == 1 && "ZBCSIJFD".indexOf(desc.charAt(0)) >= 0;
    }
}