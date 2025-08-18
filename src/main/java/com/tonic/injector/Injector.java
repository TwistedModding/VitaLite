package com.tonic.injector;

import com.tonic.injector.util.BootstrapPoisonInjector;
import com.tonic.vitalite.Main;
import com.tonic.dto.JClass;
import com.tonic.injector.annotations.*;
import com.tonic.injector.pipeline.*;
import com.tonic.injector.util.AnnotationUtil;
import com.tonic.injector.util.BootstrapAttributeCopier;
import com.tonic.injector.util.ClassNodeUtil;
import com.tonic.util.JarDumper;
import com.tonic.util.PackageUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;

public class Injector {
    private static final String MIXINS = "com.tonic.mixins";
    public static HashMap<String, ClassNode> gamepack = new HashMap<>();

    public static void patch() throws Exception {
        for (var entry : Main.LIBS.getGamepack().classes.entrySet()) {
            String name = entry.getKey();
            byte[] bytes = entry.getValue();
            gamepack.put(name, ClassNodeUtil.toNode(bytes));
        }

        HashMap<ClassNode, ClassNode> pairs = PackageUtil.getPairs(MIXINS);
        applyInterfaces(pairs);
        applyMixins(pairs);

        for (var entry : gamepack.entrySet()) {
            String name = entry.getKey();
            ClassNode classNode = entry.getValue();

            if(SignerMapper.shouldIgnore(name))
            {
                System.out.println("Skipping cert-checked class: " + name);
                continue;
            }

            Main.LIBS.getGamepack().classes.put(name, ClassNodeUtil.toBytes(classNode));

            StripAnnotationsTransformer.stripAnnotations(classNode);
            Main.LIBS.getGamepackClean().classes.put(name, ClassNodeUtil.toBytes(classNode));
        }

        JarDumper.dump(Main.LIBS.getGamepackClean().classes);
    }

    private static void applyMixins(HashMap<ClassNode, ClassNode> pairs) {
        for (ClassNode mixin : pairs.keySet()) {
            BootstrapPoisonInjector.obliterateDecompilers(mixin);
            String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
            JClass jClass = MappingProvider.getClass(gamepackName);
            ClassNode gamepackClass = gamepack.get(jClass.getObfuscatedName());
            for(FieldNode field : mixin.fields)
            {
                if(AnnotationUtil.hasAnnotation(field, Inject.class))
                {
                    InjectTransformer.patch(gamepackClass, field);
                }
                if(AnnotationUtil.hasAnnotation(field, Shadow.class))
                {
                    ShadowTransformer.patch(mixin, field);
                }
            }

            BootstrapAttributeCopier.copyBootstrapAttributesAndCallsites(mixin, gamepackClass);

            for(MethodNode method : mixin.methods)
            {
                if(AnnotationUtil.hasAnnotation(method, Inject.class) || !AnnotationUtil.hasAnyAnnotation(method))
                {
                    InjectTransformer.patch(gamepackClass, mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, MethodHook.class))
                {
                    MethodHookTransformer.patch(mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, Replace.class))
                {
                    ReplaceTransformer.patch(gamepackClass, mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, MethodOverride.class))
                {
                    MethodOverrideTransformer.patch(gamepackClass, mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, Shadow.class))
                {
                    ShadowTransformer.patch(mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, Construct.class))
                {
                    ConstructTransformer.patch(mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, Disable.class))
                {
                    DisableTransformer.patch(mixin, method);
                }
            }
        }
    }

    private static void applyInterfaces(HashMap<ClassNode, ClassNode> pairs) {
        for (var entry : pairs.entrySet()) {
            try
            {
                ClassNode api = entry.getValue();
                if(api == null)
                    continue;
                ClassNode mixin = entry.getKey();
                String gamepackName = AnnotationUtil.getAnnotation(mixin, Mixin.class, "value");
                JClass clazz = MappingProvider.getClass(gamepackName);
                ClassNode gamepackClass = gamepack.get(clazz.getObfuscatedName());
                if(gamepackClass.interfaces == null)
                {
                    gamepackClass.interfaces = new ArrayList<>();
                }
                gamepackClass.interfaces.add(api.name);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

        }
    }
}
