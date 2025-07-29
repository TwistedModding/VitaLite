package com.tonic;

import com.tonic.classloader.RLClassLoader;
import com.tonic.injector.Injector;
import com.tonic.model.Artifact;
import com.tonic.model.Libs;
import com.tonic.util.ArtifactReader;
import com.tonic.util.RuneliteConfigUtil;
import com.tonic.util.optionsparser.OptionsParser;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.util.jar.JarFile;

public class Main {
    private static final String RUNELITE_REPOSITORY_PATH = System.getProperty("user.home") + File.separator + ".runelite" + File.separator + "repository2";
    public static final OptionsParser optionsParser = new OptionsParser();
    private static URL[] URLS = null;
    public static Libs LIBS;
    public static JarFile JARFILE;
    public static RLClassLoader CLASSLOADER;
    public static RLClassLoader CTX_CLASSLOADER;
    public static Class<?> RLMAIN;

    public static void main(String[] args) throws Exception
    {
        optionsParser.parse(args);
        loadArtifacts();
        loadGamepack();
        loadClassLoader();
        Injector.patch();
        RLMAIN = CLASSLOADER.launch(args);
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
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadGamepack() throws Exception {
        JARFILE = RuneliteConfigUtil.fetchGamePack();
        Artifact gamepack = ArtifactReader.readGamepack();
        LIBS = new Libs(URLS, gamepack);
    }

    private static void loadClassLoader() {
        //PackageUtil.preloadPackages("com.tonic.api");
        System.setProperty("sun.awt.noerasebackground", "true");
        CLASSLOADER = new RLClassLoader(URLS);
        CTX_CLASSLOADER = new RLClassLoader(URLS);
        UIManager.put("ClassLoader", CLASSLOADER);
    }
}