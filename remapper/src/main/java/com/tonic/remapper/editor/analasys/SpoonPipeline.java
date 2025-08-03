package com.tonic.remapper.editor.analasys;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
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
import java.util.regex.Pattern;

public class SpoonPipeline
{
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "do","if"
    );
    private static final Pattern BAD_MNEMONICS = Pattern.compile("\\bmonitorenter\\s*\\([^)]*\\);?|\\bmonitorexit\\s*\\([^)]*\\);?");
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

        if(name.equals("do") || name.equals("if"))
        {
            name = "_" + name;
        }

        CtClass<?> ctClass = launcher.getFactory().Class().get(name);
        if(ctClass != null)
        {
            process(ctClass.getMethods());
            process(ctClass.getConstructors());
            return ParenCleaner.clean(ctClass.prettyprint()
                    .replace("\r\n", "\n")
                    .replaceAll("(?m)^\\s*\\n", ""));
        }

        CtInterface<?> ctInterface = launcher.getFactory().Interface().get(name);
        if(ctInterface != null)
        {
            process(ctInterface.getMethods());
            return ParenCleaner.clean(ctInterface.prettyprint()
                    .replace("\r\n", "\n")
                    .replaceAll("(?m)^\\s*\\n", ""));
        }

        CtEnum<?> ctEnum = launcher.getFactory().Enum().get(name);
        if(ctEnum != null)
        {
            process(ctEnum.getConstructors());
            process(ctEnum.getMethods());
            return ParenCleaner.clean(ctEnum.prettyprint()
                    .replace("\r\n", "\n")
                    .replaceAll("(?m)^\\s*\\n", ""));
        }

        System.err.println("Class " + name + " not found in the provided source.");
        return src;
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

    public static Number calculateOpaquePredicate(String name, String src)
    {
        src = sanitise(src);
        Launcher launcher = generateLauncher(name, src);
        CtClass<?> ctClass = launcher.getFactory().Class().get(name);
        CtMethod<?> method = ctClass.getMethods().iterator().next();

        return null;
    }

    private static Launcher generateLauncher(String name, String src) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(src, name + ".java"));
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
        //match and remove labels like Label_0228:
        src = src.replaceAll("(?m)^[ \t]*Label_[A-Za-z_][A-Za-z0-9_]*:\\s*", "if(true /* stripped label */ )");
        // Fix inverted null checks FIRST
        src = src.replaceAll(
                "if\\s*\\(([a-zA-Z0-9_]+)\\s*==\\s*null\\)\\s*\\{\\s*\\1\\.",
                "if ($1 != null) { $1."
        );
        src = src.replaceAll(
                "if\\s*\\(([a-zA-Z0-9_]+)\\s*==\\s*null\\)\\s*\\{\\s*return\\s+\\1\\.",
                "if ($1 != null) { return $1."
        );

        // Remove goto statements and their labels
        src = src.replaceAll("goto\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;", "// goto removed");
        // Use (?m) for multiline mode
        src = src.replaceAll("(?m)^\\s*[A-Za-z_][A-Za-z0-9_]*:\\s*$", "");

        // Fix bare semicolons (from stripped synchronized blocks)
        src = src.replaceAll(";\\s*/\\*\\s*stripped\\s*\\*/", "// synchronization removed");

        // Wrap wait() and notify() calls in synchronized blocks
        src = src.replaceAll(
                "([a-zA-Z0-9_]+\\.wait\\(\\))",
                "synchronized(this) { $1; }"
        );
        src = src.replaceAll(
                "([a-zA-Z0-9_]+\\.notify\\(\\))",
                "synchronized(this) { $1; }"
        );
        src = src.replaceAll(
                "(this\\.wait\\(\\))",
                "synchronized(this) { $1; }"
        );
        src = src.replaceAll(
                "(this\\.notify\\(\\))",
                "synchronized(this) { $1; }"
        );

        for (String kw : JAVA_KEYWORDS) {
            src = src.replaceAll(
                    "(?<![\\w$])" + kw + "(?![\\w$])(?!\\s*[\\({])",
                    "_" + kw);
        }
        src = src.replace("class do", "class _do");
        src = src.replace(".do(", "._do(");
        src = src.replace(".if(", "._if(");
        src = src.replace(" do(", " _do(");
        src = src.replace(" if(", " _if(");
        src = BAD_MNEMONICS.matcher(src).replaceAll("; /* stripped */");
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
