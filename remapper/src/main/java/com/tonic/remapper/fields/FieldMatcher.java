package com.tonic.remapper.fields;

import com.tonic.remapper.methods.MethodKey;
import com.tonic.remapper.classes.ClassMatcher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight, single-file field matcher.
 *  • descriptor / primitive-vs-object compatibility
 *  • static / instance flag
 *  • owner-class mapping boost
 *  • method-usage alignment (via your *refined* method map)
 */
public class FieldMatcher {

    public static final class Match {
        public final FieldKey oldKey;
        public final FieldKey newKey;
        public final double   score;
        Match(FieldKey o, FieldKey n, double s) { oldKey=o; newKey=n; score=s; }
        @Override public String toString() { return oldKey + " -> " + newKey + " : " + score; }
    }

    public static List<Match> matchAll(
            Map<FieldKey, FieldNode> oldFields,
            Map<FieldKey, FieldNode> newFields,
            Map<FieldKey, Set<MethodKey>> oldUses,
            Map<FieldKey, Set<MethodKey>> newUses,
            Map<MethodKey, MethodKey>      methodMap,
            Map<String, ClassMatcher.ClassMatch> classMatchByOldOwner,
            int topKPerOld
    ) {
        if (oldFields.isEmpty() || newFields.isEmpty()) return List.of();

        List<Match> collected = new ArrayList<>();

        oldFields.forEach((oldKey, oldFn) -> {
            boolean oldStatic = (oldFn.access & Opcodes.ACC_STATIC) != 0;

            /* top-K reservoir */
            PriorityQueue<Match> topK = new PriorityQueue<>(topKPerOld, Comparator.comparingDouble(m -> m.score));

            for (Map.Entry<FieldKey, FieldNode> en : newFields.entrySet()) {
                FieldKey newKey   = en.getKey();
                FieldNode newFn   = en.getValue();
                boolean   newStatic = (newFn.access & Opcodes.ACC_STATIC) != 0;

                double s = 0.0;

                // -----------------------------------------------------------------
                // 1)  descriptor compatibility  (exact 1.0, loose 0.5, incompatible → skip)
                // -----------------------------------------------------------------
                Double descScore = descriptorCompatibility(oldKey.desc, newKey.desc);
                if (descScore == null) continue;           // incompatible
                s += descScore * 0.4;

                // -----------------------------------------------------------------
                // 2)  static / instance agreement
                // -----------------------------------------------------------------
                s += (oldStatic == newStatic ? 1.0 : 0.0) * 0.1;

                // -----------------------------------------------------------------
                // 3)  owner-class mapping boost
                // -----------------------------------------------------------------
                ClassMatcher.ClassMatch cm = classMatchByOldOwner.get(oldKey.owner);
                if (cm != null && cm.newFp.internalName.equals(newKey.owner)) {
                    s += 0.3;
                }

                // -----------------------------------------------------------------
                // 4)  method-usage overlap  (only counts methods we *already* mapped)
                // -----------------------------------------------------------------
                Set<MethodKey> oldU = oldUses.getOrDefault(oldKey, Set.of());
                Set<MethodKey> newU = newUses.getOrDefault(newKey, Set.of());

                // translate old-method keys to their mapped new counterparts
                Set<MethodKey> translatedOldU = oldU.stream()
                        .map(methodMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                double usageScore = jaccard(translatedOldU, newU);
                s += usageScore * 0.5;   // strongest signal

                // reservoir logic
                Match m = new Match(oldKey, newKey, s);
                if (topK.size() < topKPerOld) {
                    topK.offer(m);
                } else if (s > Objects.requireNonNull(topK.peek()).score) {
                    topK.poll();
                    topK.offer(m);
                }
            }

            collected.addAll(topK);
        });

        collected.sort(Comparator.comparingDouble(m -> -m.score));
        return collected;
    }

    // ---------- helpers ----------

    private static Double descriptorCompatibility(String a, String b) {
        if (a.equals(b)) return 1.0;

        // primitive ↔ primitive (same sort?)  -> 0.8
        Type ta = Type.getType(a), tb = Type.getType(b);
        if (ta.getSort() == tb.getSort() && ta.getSort() != Type.OBJECT && ta.getSort() != Type.ARRAY) {
            return 0.8;
        }

        // both *object* or both array-of-object -> 0.6 (names may differ)
        boolean objA = isObjectLike(ta);
        boolean objB = isObjectLike(tb);
        if (objA && objB) return 0.6;

        return null;           // incompatible
    }

    private static boolean isObjectLike(Type t) {
        return t.getSort() == Type.OBJECT ||
                (t.getSort() == Type.ARRAY && t.getElementType().getSort() == Type.OBJECT);
    }

    private static <T> double jaccard(Set<T> x, Set<T> y) {
        if (x.isEmpty() && y.isEmpty()) return 1.0;
        Set<T> inter = new HashSet<>(x); inter.retainAll(y);
        Set<T> union = new HashSet<>(x); union.addAll(y);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }
}
