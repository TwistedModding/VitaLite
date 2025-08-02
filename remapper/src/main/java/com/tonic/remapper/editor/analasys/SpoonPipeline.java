package com.tonic.remapper.editor.analasys;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtParameterReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.AbstractFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class SpoonPipeline
{
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract","assert","boolean","break","byte","case","catch","char",
            "class","const","continue","default","do","double","else","enum",
            "extends","final","finally","float","for","goto","if","implements",
            "import","instanceof","int","interface","long","native","new",
            "package","private","protected","public","return","short","static",
            "strictfp","super","switch","synchronized","this","throw","throws",
            "transient","try","void","volatile","while","yield"
    );

    private final List<SpoonTransformer> chain = new ArrayList<>();
    public static SpoonPipeline create() {
        return new SpoonPipeline();
    }

    public SpoonPipeline add(SpoonTransformer t) {
        chain.add(t);
        return this;
    }

    public String run(String name, String src) {
        src = sanitise(src);
        Launcher launcher = generateLauncher(name, src);

        CtClass<?> ctClass = launcher.getFactory().Class().get(name);
        if (ctClass == null)
        {
            System.out.println(src);
            throw new RuntimeException("Class " + name + " not found in the provided source.");
        }

        CtExecutable<?> executable = null;
        if(!ctClass.getMethods().isEmpty())
        {
            executable = ctClass.getMethods().iterator().next();
        }

        if(executable == null && !ctClass.getConstructors().isEmpty())
        {
            executable = ctClass.getConstructors().iterator().next();
        }

        if (executable == null)
        {
            throw new RuntimeException("Method not found in class " + name + ".");
        }

        for (SpoonTransformer t : chain) {
            t.transform(executable);
        }

        return ParenCleaner.clean(ctClass.prettyprint()
                .replace("\r\n", "\n")
                .replaceAll("(?m)^\\s*\\n", ""));
    }

    private SpoonPipeline()
    {

    }
    private static Launcher generateLauncher(String name, String src) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(src, "demo/" + name + ".java"));
        launcher.getEnvironment().setEncoding(StandardCharsets.ISO_8859_1);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        return launcher;
    }

    private static String sanitise(String src) {
        for (String kw : JAVA_KEYWORDS) {
            src = src.replaceAll("\\b" + kw + "(?=\\s*[\\.:])", "_" + kw);
        }
        return src;
    }

    public interface SpoonTransformer
    {
        void transform(CtExecutable<?> method);
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
            exec.removeParameter(lastParam);
        }
    }


}
