package com.tonic.hijack;

import com.google.common.io.ByteStreams;
import com.tonic.Main;
import com.tonic.hijack.model.RuneLite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class Install
{
    public static void install(RuneLite runeLite)
    {
        try
        {
            List<Path> jarPaths = findJars();
            List<Class<?>> toLoad = new ArrayList<>();
            List<ClassByte> classes = new ArrayList<>();
            for (Path jarPath : jarPaths)
            {
                classes.addAll(listFilesInJar(runeLite, jarPath));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static List<Path> findJars()
    {
        try
        {
            Files.createDirectories(Main.RUNELITE_DIR.resolve("externalplugins"));
            Files.createDirectories(Main.RUNELITE_DIR.resolve("sideloaded-plugins"));
        }catch (IOException e){
            // ignore
        }
        try
        {
            List<Path> files = new ArrayList<>();
            try (Stream<Path> walkable = Files.walk(Main.RUNELITE_DIR.resolve("externalplugins")))
            {

                walkable.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".jar"))
                        .forEach(files::add);
            }
            try (Stream<Path> walkable = Files.walk(Main.RUNELITE_DIR.resolve("sideloaded-plugins")))
            {
                walkable.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".jar"))
                        .forEach(files::add);
            }
            return files;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static List<ClassByte> listFilesInJar(RuneLite runeLite, Path jarPath)
    {
        List<ClassByte> classes = new ArrayList<>();
        try (JarFile jarFile1 = new JarFile(jarPath.toFile()))
        {
            jarFile1.stream().forEach(jarEntry ->
            {
                if (jarEntry == null || jarEntry.isDirectory()) return;
                if(!jarEntry.getName().contains(".class")){
                    try (InputStream inputStream = jarFile1.getInputStream(jarEntry)) {
                        runeLite.getSimpleLoader().resources.put(jarEntry.getName(),
                                new ByteArrayInputStream(SimpleClassLoader.getBytes(inputStream)));
                    } catch (IOException ioException) {
                        System.err.println("Could not obtain resource entry for " + jarEntry.getName());
                    }
                }
                try (InputStream inputStream = jarFile1.getInputStream(jarEntry))
                {
                    classes.add(new ClassByte(ByteStreams.toByteArray(inputStream),
                            jarEntry.getName().replace('/', '.').substring(0,
                                    jarEntry.getName().length() - 6)));
                }
                catch (IOException ioException)
                {
                    System.out.println("Could not obtain class entry for " + jarEntry.getName());
                }
            });
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return classes;
    }
}
