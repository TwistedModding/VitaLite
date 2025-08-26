package com.tonic.vitalite;

import com.tonic.Logger;
import com.tonic.VitaLite;
import com.tonic.VitaLiteOptions;
import com.tonic.bootstrap.RLUpdater;
import com.tonic.classloader.RLClassLoader;
import com.tonic.injector.util.SignerMapper;
import com.tonic.runelite.Install;
import com.tonic.runelite.jvm.JvmParams;
import com.tonic.injector.Injector;
import com.tonic.injector.RLInjector;
import com.tonic.model.Libs;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;

public class Main {
    public static final Path RUNELITE_REPOSITORY_DIR = Path.of(System.getProperty("user.home"), ".runelite", "repository2");
    public static final Path RUNELITE_DIR = Path.of(System.getProperty("user.home"), ".runelite");
    public static final VitaLiteOptions optionsParser = new VitaLiteOptions();
    private static URL[] URLS = null;
    public static Libs LIBS;
    public static RLClassLoader CLASSLOADER;
    public static RLClassLoader CTX_CLASSLOADER;

    public static void main(String[] args) throws Exception
    {
        args = optionsParser.parse(args);
        if(!optionsParser.isSafeLaunch())
        {
            System.err.println("Safe launch not satisfied, VitaLite will not start.");
            System.exit(0);
        }
        JvmParams.set();
        RLUpdater.run();
        loadArtifacts();
        SignerMapper.map();
        loadClassLoader();
        Injector.patch();
        RLInjector.patch();
        CLASSLOADER.launch(args);
        Install.install();
        //ClientUIUpdater.inject();
        Logger.norm("VitaLite started.");
    }

    private static void loadArtifacts()
    {
        try
        {
            File[] jarfiles = RUNELITE_REPOSITORY_DIR.toFile().listFiles(f -> f.getName().endsWith(".jar")); // && !f.getName().startsWith("runelite-api-"));
            if(jarfiles == null)
                throw new Exception();
            URLS = new URL[jarfiles.length];
            for (int i = 0; i < jarfiles.length; i++)
                URLS[i] = jarfiles[i].toURI().toURL();

            LIBS = new Libs(URLS);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private static void loadClassLoader() {
        CLASSLOADER = new RLClassLoader(URLS);
        CTX_CLASSLOADER = new RLClassLoader(URLS);
        if(!isRunningFromShadedJar())
            UIManager.put("ClassLoader", CLASSLOADER);
        Thread.currentThread().setContextClassLoader(CLASSLOADER);
    }

    public static boolean isRunningFromShadedJar() {
        String jarPath = VitaLite.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        return jarPath.contains("shaded") || jarPath.endsWith(".jar");
    }
}