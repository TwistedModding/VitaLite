package com.tonic.runelite;

import com.google.common.io.ByteStreams;
import com.tonic.Main;
import com.tonic.runelite.model.PluginManager;
import com.tonic.runelite.model.RuneLite;
import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Install
{
    public static void install(RuneLite runeLite)
    {
        new Thread(() ->
        {
            try
            {
                PluginManager pluginManager = runeLite.getPluginManager();
                List<Path> jarPaths = findJars();
                List<Class<?>> toLoad = new ArrayList<>();
                List<ClassByte> classes = new ArrayList<>();
                for (Path jarPath : jarPaths)
                {
                    classes.addAll(listFilesInJar(jarPath));
                }

                int numLoaded;
                do {
                    numLoaded = 0;
                    for (int i1 = classes.size() - 1; i1 >= 0; i1--)
                    {
                        Class<?> loaded = Main.CLASSLOADER.loadClass(classes.get(i1).name, classes.get(i1).bytes);
                        if (loaded != null)
                        {
                            numLoaded++;
                            classes.remove(i1);
                        }
                        if(loaded == null)
                            continue;
                        if (loaded.getSuperclass() != null && loaded.getSuperclass().getName().endsWith(".Plugin"))
                        {
                            System.out.println("Loaded: " + loaded.getName());
                            toLoad.add(loaded);
                        }
                    }
                }while(numLoaded != 0);

                List<?> loaded = (List<?>) pluginManager.loadPlugins(toLoad);
                loaded = loaded.stream().filter(Objects::nonNull).collect(Collectors.toList());
                List<?> finalLoaded = loaded;
                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        for (var plugin : finalLoaded)
                        {
                            pluginManager.loadDefaultPluginConfiguration(Collections.singleton(plugin));
                            pluginManager.startPlugin(plugin);
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
                System.exit(0);
            }
        }).start();
    }

    public static List<Path> findJars() {
        Path external   = Main.RUNELITE_DIR.resolve("externalplugins");
        Path sideloaded = Main.RUNELITE_DIR.resolve("sideloaded-plugins");

        // ensure dirs exist
        try {
            Files.createDirectories(external);
            Files.createDirectories(sideloaded);
        } catch (IOException ignored) {}

        return Stream.of(external, sideloaded)
                .flatMap(dir -> {
                    try {
                        return Files.walk(dir);
                    } catch (IOException e) {
                        return Stream.empty();
                    }
                })
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".jar"))
                .collect(Collectors.toList());
    }

    public static List<ClassByte> listFilesInJar(Path jarPath) {
        List<ClassByte> classes = new ArrayList<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream().forEach(entry -> {
                if (entry.isDirectory()) {
                    return;
                }

                String name = entry.getName();

                try (InputStream in = jar.getInputStream(entry)) {
                    byte[] data = ByteStreams.toByteArray(in);

                    if (name.endsWith(".class")) {
                        String fqcn = name.substring(0, name.length() - 6)
                                .replace('/', '.');
                        classes.add(new ClassByte(data, fqcn));
                    } else {
                        Main.CLASSLOADER.addResource(name, new ByteArrayInputStream(data));
                    }
                } catch (IOException io) {
                    System.err.printf("Unable to process %s in %s: %s%n",
                            name, jarPath, io.getMessage());
                }
            });
        } catch (IOException io) {
            io.printStackTrace();
        }

        return classes;
    }
}
