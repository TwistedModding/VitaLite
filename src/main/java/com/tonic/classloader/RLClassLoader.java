package com.tonic.classloader;

import com.tonic.Main;
import com.tonic.api.TClient;
import com.tonic.model.Libs;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.HashMap;

public class RLClassLoader extends URLClassLoader
{
    private final HashMap<String, InputStream> resources = new HashMap<>();

    public RLClassLoader(URL[] urls)
    {
        super(urls, TClient.class.getClassLoader());
    }

    public Class<?> getMain() throws ClassNotFoundException {
        return loadClass("net.runelite.client.RuneLite");
    }

    public void launch(String[] args) throws Exception {
        String[] newArgs = new String[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = "--disable-telemetry";
        Class<?> mainClass = getMain();
        Method main = mainClass.getMethod("main", String[].class);
        StringBuilder out = new StringBuilder("RuneLite started with arguments: ");
        for (String arg : newArgs) {
            out.append(arg).append(" ");
        }
        System.out.println(out);
        main.invoke(null, (Object) newArgs);
    }

    public void addResource(String name, InputStream resource)
    {
        if (name == null || resource == null) {
            return;
        }
        resources.put(name, resource);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException
    {
        try
        {
            Class<?> loadedClass = this.findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }

            byte[] bytes = Main.LIBS.gamepackByName(name);
            if (!name.startsWith("net.runelite.") && bytes == null) {
                return super.loadClass(name);
            }

            if(bytes == null)
                bytes = Main.LIBS.classByName(name);

            loadedClass = loadArtifactClass(name, bytes);
            if(loadedClass != null)
                return loadedClass;
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
                    ProtectionDomain pd = makeProtectionDomainFor(name);
                    loadedClass = defineClass(name, bytes, 0, bytes.length, pd);
                    if (loadedClass != null) {
                        return loadedClass;
                    }
                }
            }
            catch (Exception ignored) {}
        }
        return null;
    }

    public Class<?> forName(String className) throws ClassNotFoundException {
        return Class.forName(className, true, this);
    }

    public Class<?> loadClass(String name, byte[] bytes)
    {
        try
        {
            return super.loadClass(name);
        }
        catch (ClassNotFoundException | NoClassDefFoundError e)
        {
            return lookupClass(name, bytes);
        }
    }

    public Class<?> lookupClass(String name, byte[] bytes)
    {
        Permissions perms = new Permissions();
        perms.add(new AllPermission());
        final ProtectionDomain protDomain =
                new ProtectionDomain(getClass().getProtectionDomain().getCodeSource(), perms,
                        this,
                        getClass().getProtectionDomain().getPrincipals());

        try
        {
            return defineClass(name, bytes, 0, bytes.length, protDomain);
        }
        catch (LinkageError ex)
        {
            return null;
        }
    }

    @Override
    public InputStream getResourceAsStream(String name)
    {
        if (resources.containsKey(name)) {
            return resources.get(name);
        }
        return super.getResourceAsStream(name);
    }

    private ProtectionDomain makeProtectionDomainFor(String className) {
        Libs libs = Main.LIBS;
        Certificate[] certs = libs.getClassCerts().get(className);
        URL jarUrl = libs.getUrls().get(className);

        CodeSource cs = (jarUrl != null)
                ? new CodeSource(jarUrl, certs)
                : new CodeSource(getClass().getProtectionDomain().getCodeSource().getLocation(), (Certificate[]) null);

//        Permissions perms = new Permissions();
//        perms.add(new AllPermission());
        return new ProtectionDomain(cs, null, this, null);
    }
}
