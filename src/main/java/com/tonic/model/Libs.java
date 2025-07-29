package com.tonic.model;

import com.tonic.util.ArtifactReader;
import com.tonic.util.RuneliteConfigUtil;
import lombok.Getter;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@Getter
public class Libs
{
    private final List<Artifact> artifacts;
    private final Artifact gamepack;
    private final Artifact gamepackClean = new Artifact();

    public Libs(URL[] urls, Artifact gamepack)
    {
        this.artifacts = ArtifactReader.read(urls);
        this.gamepack = gamepack;
    }

    public byte[] gamepackByName(String name)
    {
        return gamepack.classes.get(name);
    }

    public Artifact artifactByName(String artifactName)
    {
        return artifacts.stream()
                .filter(art -> art.artifactName.equals(artifactName))
                .findFirst()
                .orElse(null);
    }

    public byte[] classByName(String className) {
        for (Artifact artifact : artifacts) {
            byte[] classBytes = artifact.classes.get(className);
            if (classBytes != null) return classBytes;
        }
        return null;
    }

    public Iterable<Map.Entry<String, byte[]>> iterate() {
        return () -> artifacts.stream()
                .flatMap(artifact -> artifact.classes.entrySet().stream())
                .iterator();
    }
    public Stream<Map.Entry<String, byte[]>> stream() {
        return artifacts.stream()
                .filter(artifact -> artifact.classes != null)
                .flatMap(artifact -> artifact.classes.entrySet().stream());
    }
}
