package com.tonic.injector;

import com.tonic.Main;
import com.tonic.dto.JClass;
import com.tonic.injector.annotations.*;
import com.tonic.injector.pipeline.*;
import com.tonic.util.AnnotationUtil;
import com.tonic.util.ClassNodeUtil;
import com.tonic.util.JarDumper;
import com.tonic.util.PackageUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;

public class Injector {
    private static final String MIXINS = "com.tonic.mixins";

    public static void patch() throws Exception {
        HashMap<String, ClassNode> gamepack = new HashMap<>();
        for (var entry : Main.LIBS.getGamepack().classes.entrySet()) {
            String name = entry.getKey();
            byte[] bytes = entry.getValue();
            gamepack.put(name, ClassNodeUtil.toNode(bytes));
        }

        HashMap<ClassNode, ClassNode> pairs = PackageUtil.getPairs(MIXINS);
        applyInterfaces(pairs, gamepack);
        applyMixins(pairs, gamepack);

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

    private static void applyMixins(HashMap<ClassNode, ClassNode> pairs, HashMap<String, ClassNode> gamepack) {
        for (ClassNode mixin : pairs.keySet()) {
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
                    ShadowTransformer.patch(gamepackClass, mixin, field);
                }
            }

            for(MethodNode method : mixin.methods)
            {
                if(AnnotationUtil.hasAnnotation(method, Inject.class) || !AnnotationUtil.hasAnyAnnotation(method))
                {
                    InjectTransformer.patch(gamepackClass, mixin, method);
                }
                if(AnnotationUtil.hasAnnotation(method, MethodHook.class))
                {
                    MethodHookTransformer.patch(gamepackClass, mixin, method);
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
                    //TODO: implement
                }
            }
        }
    }

    private static void applyInterfaces(HashMap<ClassNode, ClassNode> pairs, HashMap<String, ClassNode> gamepack) {
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
