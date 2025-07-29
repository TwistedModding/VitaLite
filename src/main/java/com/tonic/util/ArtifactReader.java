package com.tonic.util;

import com.tonic.model.Libs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

public class ArtifactReader {
    public static void read(Libs libs, URL[] urls) throws Exception {
        if (urls == null) {
            return;
        }

        for (URL url : urls) {
            if (url == null) continue;

            try {
                readJarFromUrl(libs, url);
            } catch (IOException e) {
                System.err.println("Error reading JAR from URL: " + url + " - " + e.getMessage());
            }
        }
        readGamepack(libs);
    }

    private static void readJarFromUrl(Libs libs, URL url) throws IOException {
        try (JarInputStream jarIn = new JarInputStream(url.openStream())) {
            JarEntry entry;

            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if (!entry.isDirectory()) {
                    byte[] entryBytes = readAllBytes(jarIn);

                    if (entryName.endsWith(".class")) {
                        String className = entryName.replace('/', '.')
                                .substring(0, entryName.length() - 6);
                        if(className.startsWith("net.runelite"))
                        {
                            libs.getRunelite().classes.put(className, entryBytes);
                        }
                        else
                        {
                            libs.getOther().classes.put(className, entryBytes);
                        }
                    }
                }
                jarIn.closeEntry();
            }
        }
    }

    private static void readGamepack(Libs libs) throws Exception
    {
        try (JarFile jarFile = RuneliteConfigUtil.fetchGamePack())
        {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class") && !entry.getName().contains("/"))
                {
                    try (InputStream is = jarFile.getInputStream(entry))
                    {
                        byte[] entryBytes = is.readAllBytes();
                        String className = entry.getName()
                                .replace('/', '.')
                                .substring(0, entry.getName().length() - 6);
                        libs.getGamepack().classes.put(className, entryBytes);
                    }
                }
            }
        }
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }

        return baos.toByteArray();
    }
}