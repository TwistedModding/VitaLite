package com.tonic.remapper.editor.analasys;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.AbstractFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SpoonPipeline
{
    private final List<SpoonTransformer> chain = new ArrayList<>();
    public static final List<String> cleanedMethods = new ArrayList<>();
    public static SpoonPipeline create() {
        return new SpoonPipeline();
    }

    public SpoonPipeline add(SpoonTransformer t) {
        chain.add(t);
        return this;
    }

    public String run(String name, String src) {
        // Extract package and imports from original source
        String packageDecl = extractPackage(src);
        String imports = extractImports(src);

        Launcher launcher = generateLauncher(name, src);

        if(name.equals("do") || name.equals("if")) {
            name = "_" + name;
        }

        CtType<?> ctType = null;

        // Try all type kinds
        ctType = launcher.getFactory().Class().get(name);
        if (ctType == null) ctType = launcher.getFactory().Interface().get(name);
        if (ctType == null) ctType = launcher.getFactory().Enum().get(name);
        if (ctType == null) ctType = launcher.getFactory().Annotation().get(name);

        if (ctType == null) {
            System.err.println("Type " + name + " not found in the provided source.");
            return src;
        }

        // Process the type
        if (ctType instanceof CtClass<?>) {
            process(((CtClass<?>) ctType).getMethods());
            process(((CtClass<?>) ctType).getConstructors());
        } else if (ctType instanceof CtInterface<?>) {
            process(((CtInterface<?>) ctType).getMethods());
        } else if (ctType instanceof CtEnum<?>) {
            process(((CtEnum<?>) ctType).getMethods());
            process(((CtEnum<?>) ctType).getConstructors());
        }

        // Build the output
        StringBuilder result = new StringBuilder();

        // Add package if present
        if (!packageDecl.isEmpty()) {
            result.append(packageDecl).append("\n\n");
        }

        // Add imports if present
        if (!imports.isEmpty()) {
            result.append(imports).append("\n");
        }

        // Add the processed type
        result.append(ctType.prettyprint());

        return result.toString();
    }

    private SpoonPipeline()
    {
    }

    private void process(Set<? extends CtExecutable<?>> executables) {
        for (CtExecutable<?> executable : executables) {
            for (SpoonTransformer t : chain) {
                t.transform(executable);
            }
        }
    }

    private static Launcher generateLauncher(String name, String src) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(src, name + ".java"));
        launcher.getEnvironment().setEncoding(StandardCharsets.ISO_8859_1);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.buildModel();
        return launcher;
    }

    public interface SpoonTransformer
    {
        void transform(CtExecutable<?> method);
    }

    public static class paramCleaner implements SpoonTransformer
    {

        @Override
        public void transform(CtExecutable<?> method) {
            method.getBody()
                    .getElements(new TypeFilter<>(CtInvocation.class))
                    .forEach(invocation -> {
                        var arguments = invocation.getArguments();
                        String name = invocation.getExecutable().getDeclaringType().getSimpleName() + "." + invocation.getExecutable().getSignature();
                        if (!arguments.isEmpty() && cleanedMethods.contains(name)) {
                            System.out.println("Cleaning invocation: " + name);
                            // Remove the last argument
                            ArrayList<CtExpression<?>> newArguments = new ArrayList<>(invocation.getArguments());
                            newArguments.remove(newArguments.size() - 1);
                            invocation.setArguments(newArguments);
                        }
                    });
        }
    }

    public static class OpaquePredicateCleaner implements SpoonTransformer {

        private static final String ISE = IllegalStateException.class.getName();

        @Override
        public void transform(CtExecutable<?> exec) {
            if (exec.getParameters().isEmpty() || !exec.toString().contains("IllegalStateException")) {
                return;
            }

            CtParameter<?> lastParam = exec.getParameters()
                    .get(exec.getParameters().size() - 1);
            final CtParameterReference<?> lastRef = lastParam.getReference();

            Filter<CtVariableAccess<?>> lastParamAccess =
                    new AbstractFilter<>(CtVariableAccess.class) {
                        @Override
                        public boolean matches(CtVariableAccess<?> a) {
                            return a.getVariable() != null
                                    && a.getVariable().equals(lastRef);
                        }
                    };

            java.util.function.Predicate<CtStatement> bodyIsJustISE =
                    st -> {
                        if (st == null) return false;

                        CtStatement top = st;

                        if (top instanceof CtBlock) {
                            CtBlock<?> blk = (CtBlock<?>) top;
                            if (blk.getStatements().size() != 1) return false;
                            top = blk.getStatement(0);
                        }

                        if (!(top instanceof CtThrow)) return false;
                        CtThrow thr = (CtThrow) top;

                        CtExpression<?> thrown = thr.getThrownExpression();
                        return thrown != null
                                && ISE.equals(thrown.getType().getQualifiedName());
                    };

            for (CtIf ctIf : exec.getElements(new TypeFilter<CtIf>(CtIf.class))) {

                boolean opaqueCond =
                        ctIf.getCondition() != null
                                && !ctIf.getCondition().getElements(lastParamAccess).isEmpty();

                boolean uselessBody =
                        bodyIsJustISE.test(ctIf.getThenStatement())
                                || bodyIsJustISE.test(ctIf.getElseStatement());

                if (opaqueCond || uselessBody) {
                    ctIf.delete();
                }
            }

            for (CtConditional<?> cond
                    : exec.getElements(new TypeFilter<CtConditional<?>>(CtConditional.class))) {

                boolean opaqueCond =
                        cond.getCondition() != null
                                && !cond.getCondition().getElements(lastParamAccess).isEmpty();

                if (opaqueCond) {
                    cond.delete();
                }
            }

            for (CtWhile w : exec.getElements(new TypeFilter<>(CtWhile.class))) {
                boolean opaqueCond =
                        !w.getLoopingExpression().getElements(lastParamAccess).isEmpty();
                if (opaqueCond || bodyIsJustISE.test(w.getBody())) {
                    w.delete();
                }
            }

            for (CtDo d : exec.getElements(new TypeFilter<>(CtDo.class))) {
                boolean opaqueCond =
                        !d.getLoopingExpression().getElements(lastParamAccess).isEmpty();
                if (opaqueCond || bodyIsJustISE.test(d.getBody())) {
                    d.delete();
                }
            }

            for (CtFor f : exec.getElements(new TypeFilter<>(CtFor.class))) {
                CtExpression<?> c = f.getExpression();
                boolean opaqueCond =
                        c != null && !c.getElements(lastParamAccess).isEmpty();
                if (opaqueCond || bodyIsJustISE.test(f.getBody())) {
                    f.delete();
                }
            }

            for (CtForEach fe : exec.getElements(new TypeFilter<>(CtForEach.class))) {
                CtExpression<?> c = fe.getExpression();
                boolean opaqueCond =
                        c != null && !c.getElements(lastParamAccess).isEmpty();
                if (opaqueCond || bodyIsJustISE.test(fe.getBody())) {
                    fe.delete();
                }
            }

            /* drop now-unused opaque parameter */
            CtExecutableReference<?> execRef = exec.getReference();
            CtTypeReference<?> declaringTypeRef = execRef.getDeclaringType();
            String className = declaringTypeRef.getSimpleName();
            cleanedMethods.add(className + "." + exec.getSignature());
            exec.removeParameter(lastParam);
        }
    }



    private String extractImports(String src) {
        StringBuilder imports = new StringBuilder();
        String[] lines = src.split("\n");
        boolean inImports = false;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("package ")) {
                inImports = true;
                continue;
            }
            if (line.startsWith("import ")) {
                imports.append(line).append("\n");
                inImports = true;
            } else if (inImports && !line.isEmpty() && !line.startsWith("import ")) {
                // We've reached the end of imports
                break;
            }
        }

        return imports.toString();
    }

    private String extractPackage(String src) {
        String[] lines = src.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("package ")) {
                return line;
            }
        }
        return "";
    }
}
