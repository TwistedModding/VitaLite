package com.tonic;

import com.tonic.bootstrap.RLUpdater;
import com.tonic.classloader.RLClassLoader;
import com.tonic.hijack.model.RuneLite;
import com.tonic.injector.Injector;
import com.tonic.injector.RLInjector;
import com.tonic.model.Artifact;
import com.tonic.model.Libs;
import com.tonic.util.ArtifactReader;
import com.tonic.util.RuneliteConfigUtil;
import com.tonic.util.optionsparser.OptionsParser;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class Main {
    private static final String RUNELITE_REPOSITORY_PATH = System.getProperty("user.home") + File.separator + ".runelite" + File.separator + "repository2";
    public static final Path RUNELITE_DIR = Path.of(System.getProperty("user.home"), ".runelite");
    public static final OptionsParser optionsParser = new OptionsParser();
    private static URL[] URLS = null;
    public static Libs LIBS;
    public static RLClassLoader CLASSLOADER;
    public static RLClassLoader CTX_CLASSLOADER;
    public static RuneLite RUNELITE;

    public static void main(String[] args) throws Exception
    {
        RLUpdater.run();
        optionsParser.parse(args);
        args = optionsParser.filter(args);
        loadArtifacts();
        loadClassLoader();
        Injector.patch();
        RLInjector.patch();
        RUNELITE = new RuneLite(CLASSLOADER.launch(args));
    }

    public static void loadArtifacts()
    {
        try
        {
            File[] jarfiles = new File(RUNELITE_REPOSITORY_PATH).listFiles(f -> f.getName().endsWith(".jar"));
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