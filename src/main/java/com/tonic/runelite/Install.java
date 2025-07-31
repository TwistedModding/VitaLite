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
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Install
{
    public static void start(RuneLite runeLite) {
        Runnable task = () -> {
            try {
                PluginManager pluginManager = runeLite.getPluginManager();

                /* ---------- gather classes from every plugin jar ---------- */
                List<ClassByte> pending = findJars().stream()       // locate jars
                        .flatMap(jar -> listFilesInJar(jar).stream())// read classes/resources
                        .collect(Collectors.toCollection(ArrayList::new));

                List<Class<?>> plugins = new ArrayList<>();

                /* ---------- resolve classes until nothing new loads ---------- */
                // -----------------------------------------------------------------------------
// 2.  Resolve & load classes that depend on one-another
// -----------------------------------------------------------------------------
                while (!pending.isEmpty()) {
                    int loadedThisPass = 0;

                    for (Iterator<ClassByte> it = pending.iterator(); it.hasNext(); ) {
                        ClassByte cb  = it.next();
                        Class<?>  cls = Main.CLASSLOADER.loadClass(cb.name, cb.bytes);
                        if (cls == null) continue;
                        it.remove();
                        loadedThisPass++;
                        Class<?> parent = cls.getSuperclass();
                        if (parent != null && parent.getName().endsWith(".Plugin")) {
                            System.out.println("Loaded: " + cls.getName());
                            plugins.add(cls);
                        }
                    }
                    if (loadedThisPass == 0) {
                        break;
                    }
                }

                List<?> instances = pluginManager.loadPlugins(plugins).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                SwingUtilities.invokeAndWait(() -> instances.forEach(p -> {
                    try {
                        pluginManager.loadDefaultPluginConfiguration(Collections.singleton(p));
                        pluginManager.startPlugin(p);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }));
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(0);
            }
        };

        new Thread(task, "plugin-installer").start();
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
