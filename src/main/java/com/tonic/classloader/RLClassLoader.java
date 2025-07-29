package com.tonic.classloader;

import com.tonic.Main;
import com.tonic.api.TClient;
import com.tonic.injector.RLInjector;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class RLClassLoader extends URLClassLoader
{
    private static final CodeSource CODE_SOURCE =
            Main.class.getProtectionDomain().getCodeSource();     // loaded earlier via URLCL

    private static final ProtectionDomain PROTECTION_DOMAIN =
            new ProtectionDomain(CODE_SOURCE,
                    Main.class.getProtectionDomain().getPermissions());
    public RLClassLoader(URL[] urls)
    {
        super(urls, TClient.class.getClassLoader());
    }

    public Class<?> launch(String[] args) throws Exception {
        String[] newArgs = new String[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = "--disable-telemetry";
        Class<?> mainClass = loadClass("net.runelite.client.RuneLite");
        Method main = mainClass.getMethod("main", String[].class);
        main.invoke(null, (Object) newArgs);
        return mainClass;
    }



    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException
    {
        return getLoadedClass(name);
    }

    public Class<?> getLoadedClass(String name) throws ClassNotFoundException
    {
        try
        {
            Class<?> loadedClass = this.findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }

            if (name.startsWith("java.") || name.startsWith("sun.") || name.startsWith("jdk.") || name.startsWith("com.tonic.")) {
                return super.loadClass(name);
            }

            byte[] bytes = Main.LIBS.gamepackByName(name);
            loadedClass = loadArtifactClass(name, bytes);
            if(loadedClass != null) return loadedClass;

            bytes = Main.LIBS.classByName(name);
            loadedClass = loadArtifactClass(name, bytes);
            if(loadedClass != null) return loadedClass;
        }
        catch (Exception ignored) {}
        return super.loadClass(name);
    }

    private Class<?> loadArtifactClass(String name, byte[] bytes)
    {
        Class<?> loadedClass;
        if(bytes != null)
        {
            try {
                if (bytes.length > 0) {
                    loadedClass = defineClass(name, bytes, 0, bytes.length);
                    if (loadedClass != null) {
                        return loadedClass;
                    }
                }
            }
            catch (Exception ignored) {}
        }
        return null;
    }
}
