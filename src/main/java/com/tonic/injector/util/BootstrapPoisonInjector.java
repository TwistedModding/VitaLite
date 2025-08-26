package com.tonic.injector.util;

import com.tonic.injector.annotations.SkipPoison;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

import java.util.*;

/**
 * Injects the EXACT decompiler-crashing pattern from the wild.
 * Fixed to match the recipe format properly.
 */
public class BootstrapPoisonInjector {

    private static final Random random = new Random();
    private static final Set<CallSite> callSites = new HashSet<>();

    static
    {
        callSites.add(new CallSite("java/lang/String", "valueOf"));

        callSites.add(new CallSite("java/lang/Long", "toString"));
        callSites.add(new CallSite("java/lang/Long", "toHexString"));
        callSites.add(new CallSite("java/lang/Long", "toOctalString"));
        callSites.add(new CallSite("java/lang/Long", "toBinaryString"));
        callSites.add(new CallSite("java/lang/Long", "toUnsignedString"));

        callSites.add(new CallSite("java/lang/Thread", "getName"));

    }

    public static class Config {
        public boolean useOpaqueDeadCode = true;
        public double injectionChance = 1.0;
        public boolean injectIntoConstructors = false;
        public boolean injectIntoStaticInit = false;

        public static Config aggressive() {
            Config config = new Config();
            config.useOpaqueDeadCode = true;
            config.injectionChance = 1.0;
            config.injectIntoConstructors = true;
            config.injectIntoStaticInit = false;
            return config;
        }
    }

    public static void crashAllMethods(ClassNode classNode, Config config) {
        if(AnnotationUtil.hasAnnotation(classNode, SkipPoison.class))
            return;

        // Inject into methods
        for (MethodNode method : classNode.methods) {
            if (shouldInjectIntoMethod(classNode, method, config)) {
                String value = "";
                if(AnnotationUtil.hasAnnotation(method, SkipPoison.class))
                {
                    value = AnnotationUtil.getAnnotation(method, SkipPoison.class, "value");
                    if(value == null || value.isEmpty())
                        continue;
                }
                injectWildPoison(classNode, method, config, value);
            }
        }
    }

    private static void injectWildPoison(ClassNode classNode, MethodNode method, Config config, String value) {
        var points = findSafeInjectionPoint(method, value);
        if (points.isEmpty()) return;


        InsnList poison = new InsnList();

        for( AbstractInsnNode point : points) {
            if (config.useOpaqueDeadCode) {
                // Opaque predicate wrapper
                Label deadCode = new Label();
                Label skip = new Label();

                // System.nanoTime() % 2 == 3 (always false)
                poison.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
                poison.add(new LdcInsnNode(2L));
                poison.add(new InsnNode(LREM));
                poison.add(new LdcInsnNode(3L));
                poison.add(new InsnNode(LCMP));
                poison.add(new JumpInsnNode(IFEQ, new LabelNode(deadCode)));
                poison.add(new JumpInsnNode(GOTO, new LabelNode(skip)));

                poison.add(new LabelNode(deadCode));

                // Randomly choose pattern
                addWorkingPattern1(poison);

                poison.add(new InsnNode(POP));
                poison.add(new JumpInsnNode(GOTO, new LabelNode(skip)));

                poison.add(new LabelNode(skip));
            } else {
                addWorkingPattern1(poison);
                poison.add(new InsnNode(POP));
            }

            method.instructions.insertBefore(point, poison);
        }
    }

    /**
     * Pattern 1: Single argument with constant
     * Recipe: "\u0001\u0002" means concat(arg, constant)
     */
    private static void addWorkingPattern1(InsnList insns) {
        long val1 = random.nextLong();
        long val2 = random.nextLong();

        CallSite callSite = callSites.stream()
                .skip(random.nextInt(callSites.size()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No CallSite found"));

        // Push one value for concatenation
        insns.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        insns.add(new LdcInsnNode(val1));
        insns.add(new InsnNode(LREM));

        // StringConcatFactory handle
        Handle concatHandle = new Handle(
                H_INVOKESTATIC,
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                false
        );

        // ConstantBootstraps.invoke handle
        Handle bootstrapHandle = new Handle(
                H_INVOKESTATIC,
                "java/lang/invoke/ConstantBootstraps",
                "invoke",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
                false
        );

        // Helper method handle
        Handle helperHandle = new Handle(
                H_INVOKESTATIC,
                callSite.getClassName(),
                callSite.methodName,
                "(J)Ljava/lang/String;",
                false
        );

        // ConstantDynamic with NULL CHAR name
        ConstantDynamic constantDynamic = new ConstantDynamic(
                "\u0000\u0000\u0000",  // NULL CHAR name - the poison!
                "Ljava/lang/String;",
                bootstrapHandle,
                helperHandle,
                val2
        );

        // Bootstrap args: recipe + constant
        Object[] bootstrapArgs = {
                "\u0001\u0002",  // Recipe: arg + constant
                constantDynamic  // The constant (ConstantDynamic)
        };

        // InvokeDynamic with normal name
        insns.add(new InvokeDynamicInsnNode(
                "makeConcatWithConstants",  // Normal name
                "(J)Ljava/lang/String;",    // Takes long, returns String
                concatHandle,
                bootstrapArgs
        ));
    }

    private static List<AbstractInsnNode> findSafeInjectionPoint(MethodNode method, String value) {
        if(!value.isEmpty())
        {
            System.out.println("Searching for static field " + value + " in " + method.name);
            //look for static field call with the name of value
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if (fieldInsn.name.equals(value)) {
                        System.out.println("Found static field " + value + " in " + method.name);
                        return Collections.singletonList(insn);
                    }
                }
            }
            return Collections.emptyList();
        }
        int max = 3;
        List<AbstractInsnNode> safePoints = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (isStringReference(insn)) {
                safePoints.add(insn);
            }
            if (safePoints.size() >= max) {
                break;
            }
        }
        return safePoints;
    }

    public static boolean isStringReference(AbstractInsnNode insn) {
        if (insn == null) return false;

        switch (insn.getType()) {
            case AbstractInsnNode.LDC_INSN:
                // String literal
                return ((LdcInsnNode) insn).cst instanceof String;

            case AbstractInsnNode.FIELD_INSN:
                // String field access
                FieldInsnNode field = (FieldInsnNode) insn;
                return (insn.getOpcode() == GETSTATIC || insn.getOpcode() == GETFIELD)
                        && field.desc.equals("Ljava/lang/String;");

            case AbstractInsnNode.METHOD_INSN:
                // Method returning String
                MethodInsnNode method = (MethodInsnNode) insn;
                return method.desc.endsWith(")Ljava/lang/String;");

            case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
                // InvokeDynamic returning String (like StringConcatFactory)
                InvokeDynamicInsnNode invokeDyn = (InvokeDynamicInsnNode) insn;
                return invokeDyn.desc.endsWith(")Ljava/lang/String;");

            default:
                return false;
        }
    }

    private static boolean shouldInjectIntoMethod(ClassNode classNode, MethodNode method, Config config) {
        if ((method.access & ACC_NATIVE) != 0) return false;
        if ((method.access & ACC_ABSTRACT) != 0) return false;
        return method.instructions != null && method.instructions.size() > 0;
    }

    public static void obliterateDecompilers(ClassNode classNode) {
        crashAllMethods(classNode, Config.aggressive());
    }

    @Getter
    @RequiredArgsConstructor
    private static class CallSite
    {
        private final String className;
        private final String methodName;
    }
}