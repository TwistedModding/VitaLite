package com.tonic.methods;

import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Builds a crude call graph from MethodNodes. Only records invoked methods as edges.
 */
public class CallGraphExtractor
{
    /**
     * Extracts the call graph: for each method key, the set of methods it calls.
     * Only includes target methods that appear in the provided method set if restrictToProvided is true.
     */
    public static Map<MethodKey, Set<MethodKey>> extractCallGraph(
            Map<MethodKey, MethodNode> methods,
            boolean restrictToProvided
    ) {
        Map<MethodKey, Set<MethodKey>> callGraph = new HashMap<>();
        Set<MethodKey> available = methods.keySet();

        for (Map.Entry<MethodKey, MethodNode> entry : methods.entrySet()) {
            MethodKey caller = entry.getKey();
            MethodNode mn = entry.getValue();

            Set<MethodKey> callees = new HashSet<>();

            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    MethodKey callee = new MethodKey(mi.owner, mi.name, mi.desc);
                    if (!restrictToProvided || available.contains(callee)) {
                        callees.add(callee);
                    }
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    // Optionally, you could try to resolve the bootstrap to an actual target;
                    // for scaffolding we skip dynamic invocations, or you could encode the indy
                    // as a synthetic MethodKey if desired.
                }
            }

            callGraph.put(caller, Collections.unmodifiableSet(callees));
        }

        return callGraph;
    }

    /**
     * Inverts a call graph: returns for each method key the set of methods that call it.
     */
    public static Map<MethodKey, Set<MethodKey>> invertCallGraph(Map<MethodKey, Set<MethodKey>> graph) {
        Map<MethodKey, Set<MethodKey>> inverse = new HashMap<>();
        for (Map.Entry<MethodKey, Set<MethodKey>> e : graph.entrySet()) {
            MethodKey caller = e.getKey();
            for (MethodKey callee : e.getValue()) {
                inverse.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
            }
        }
        // make unmodifiable sets
        for (Map.Entry<MethodKey, Set<MethodKey>> e : new HashMap<>(inverse).entrySet()) {
            inverse.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        return inverse;
    }
}
