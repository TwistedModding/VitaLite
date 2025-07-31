package com.tonic;

import com.tonic.bootstrap.RLUpdater;
import com.tonic.classloader.RLClassLoader;
import com.tonic.runelite.Install;
import com.tonic.runelite.model.RuneLite;
import com.tonic.injector.Injector;
import com.tonic.injector.RLInjector;
import com.tonic.model.Libs;
import com.tonic.util.ReflectUtil;
import com.tonic.util.optionsparser.OptionsParser;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;

public class Main {
    public static final Path RUNELITE_REPOSITORY_DIR = Path.of(System.getProperty("user.home"), ".runelite", "repository2");
    public static final Path RUNELITE_DIR = Path.of(System.getProperty("user.home"), ".runelite");
    public static final OptionsParser optionsParser = new OptionsParser();
    private static URL[] URLS = null;
    public static Libs LIBS;
    public static RLClassLoader CLASSLOADER;
    public static RLClassLoader CTX_CLASSLOADER;

    private static RuneLite RUNELITE;

    public static void setRunelite(RuneLite runelite) {
        System.out.println("Setting RuneLite instance: " + runelite);
        RUNELITE = runelite;
    }

    public static RuneLite getRunelite() {
        return RUNELITE;
    }

    public static void main(String[] args) throws Exception
    {
        RLUpdater.run();
        args = optionsParser.parse(args);
        loadArtifacts();
        loadClassLoader();
        Injector.patch();
        RLInjector.patch();
        CLASSLOADER.launch(args);
    }

    public static void loadArtifacts()
    {
        try
        {
            File[] jarfiles = RUNELITE_REPOSITORY_DIR.toFile().listFiles(f -> f.getName().endsWith(".jar"));
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
        System.setProperty("sun.awt.noerasebackground", "true");
        CLASSLOADER = new RLClassLoader(URLS);
        CTX_CLASSLOADER = new RLClassLoader(URLS);
        UIManager.put("ClassLoader", CLASSLOADER);
    }
}