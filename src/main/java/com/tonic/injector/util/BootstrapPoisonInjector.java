package com.tonic.injector.util;

import com.tonic.injector.annotations.SkipPoison;
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
        // Add helper method
        //addHelperMethod(classNode);

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

        //System.out.println("Injecting poison into " + classNode.name + "." + method.name + method.desc + " at " + points.size() + " points");

        InsnList poison = new InsnList();

        for( AbstractInsnNode point : points) {
            if (config.useOpaqueDeadCode) {
                // Opaque predicate wrapper
                Label deadCode = new Label();
                Label skip = new Label();

                Label l2 = new Label();
                Label l3 = new Label();

                // System.nanoTime() % 2 == 3 (always false)
                //1
                poison.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
                poison.add(new LdcInsnNode(2L));
                poison.add(new InsnNode(LREM));
                poison.add(new LdcInsnNode(3L));
                poison.add(new InsnNode(LCMP));
                poison.add(new JumpInsnNode(IFEQ, new LabelNode(deadCode)));
                poison.add(new JumpInsnNode(GOTO, new LabelNode(skip)));

                poison.add(new LabelNode(deadCode));

                // Randomly choose pattern
                if (random.nextBoolean()) {
                    addWorkingPattern1(poison, classNode);
                } else {
                    addWorkingPattern2(poison, classNode);
                }

                poison.add(new InsnNode(POP));
                poison.add(new JumpInsnNode(GOTO, new LabelNode(skip)));

                poison.add(new LabelNode(skip));
            } else {
                addWorkingPattern1(poison, classNode);
                poison.add(new InsnNode(POP));
            }

            method.instructions.insertBefore(point, poison);
        }
    }

    /**
     * Pattern 1: Single argument with constant
     * Recipe: "\u0001\u0002" means concat(arg, constant)
     */
    private static void addWorkingPattern1(InsnList insns, ClassNode classNode) {
        // Push one value for concatenation
        insns.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        insns.add(new LdcInsnNode(1000L));
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
                "client",
                "oe",
                "(J)Ljava/lang/String;",
                false
        );

        // ConstantDynamic with NULL CHAR name
        ConstantDynamic constantDynamic = new ConstantDynamic(
                "\u0000\u0000\u0000",  // NULL CHAR name - the poison!
                "Ljava/lang/String;",
                bootstrapHandle,
                helperHandle,
                0L
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

    /**
     * Pattern 2: No arguments, just constant
     * Recipe: "\u0002" means just the constant
     */
    private static void addWorkingPattern2(InsnList insns, ClassNode classNode) {
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
                classNode.name,
                "o1",
                "(J)Ljava/lang/String;",
                false
        );

        // ConstantDynamic with NULL CHAR name
        ConstantDynamic constantDynamic = new ConstantDynamic(
                "\u0000\u0000\u0000",  // NULL CHAR name
                "Ljava/lang/String;",
                bootstrapHandle,
                helperHandle,
                System.currentTimeMillis() % 100
        );

        // Bootstrap args: recipe + constant
        Object[] bootstrapArgs = {
                "\u0002",  // Recipe: just constant
                constantDynamic
        };

        // InvokeDynamic - no arguments needed
        insns.add(new InvokeDynamicInsnNode(
                "makeConcatWithConstants",
                "()Ljava/lang/String;",  // No args, returns String
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
        //if ("<clinit>".equals(method.name) && !config.injectIntoStaticInit) return false;
        //if ("<init>".equals(method.name) && !config.injectIntoConstructors) return false;
        //if ("o1".equals(method.name)) return false;
        //if(method.name.length() < 5 && method.instructions.size() > 100) return false;
        //if(classNode.name.equals("client") || classNode.name.equals("dw")) return false;
        if ((method.access & ACC_NATIVE) != 0) return false;
        if ((method.access & ACC_ABSTRACT) != 0) return false;
        return method.instructions != null && method.instructions.size() > 0;
    }

    public static void obliterateDecompilers(ClassNode classNode) {
        crashAllMethods(classNode, Config.aggressive());
    }
}