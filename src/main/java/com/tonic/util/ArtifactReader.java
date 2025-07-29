package com.tonic.util;

import com.tonic.Main;
import com.tonic.model.Artifact;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

public class ArtifactReader {
    public static List<Artifact> read(URL[] urls) {
        List<Artifact> artifacts = new ArrayList<>();

        if (urls == null) {
            return artifacts;
        }

        for (URL url : urls) {
            if (url == null) continue;

            try {
                Artifact artifact = readJarFromUrl(url);
                artifacts.add(artifact);
            } catch (IOException e) {
                System.err.println("Error reading JAR from URL: " + url + " - " + e.getMessage());
            }
        }
        return artifacts;
    }

    private static Artifact readJarFromUrl(URL url) throws IOException {
        Artifact artifact = new Artifact();

        // Extract artifact name from URL
        String urlPath = url.toString();
        int lastSlash = urlPath.lastIndexOf('/');
        artifact.artifactName = (lastSlash >= 0) ? urlPath.substring(lastSlash + 1) : urlPath;

        try (JarInputStream jarIn = new JarInputStream(url.openStream())) {
            JarEntry entry;

            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                // Skip directories
                if (!entry.isDirectory()) {
                    // Read the entry bytes
                    byte[] entryBytes = readAllBytes(jarIn);

                    if (entryName.endsWith(".class")) {
                        // Convert path to FQDN (replace / with . and remove .class extension)
                        String className = entryName.replace('/', '.')
                                .substring(0, entryName.length() - 6);
                        if(!className.startsWith("net.runelite.api") || className.endsWith("OverlayIndex"))
                        {
                            artifact.classes.put(className, entryBytes);
                        }
                    }
                }

                jarIn.closeEntry();
            }
        }

        return artifact;
    }

    public static Artifact readGamepack() throws Exception
    {
        Artifact artifact = new Artifact();
        try (JarFile jarFile = Main.JARFILE)
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
                        artifact.classes.put(className, entryBytes);
                    }
                }
            }
        }

        return artifact;
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