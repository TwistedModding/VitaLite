package com.tonic.model;

import com.tonic.util.ArtifactReader;
import com.tonic.util.RuneliteConfigUtil;
import lombok.Getter;
import lombok.Setter;

import java.net.URL;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@Getter
@Setter
public class Libs
{
    private final Artifact other = new Artifact();
    private final Artifact runelite = new Artifact();
    private final Artifact gamepack = new Artifact();
    private final Artifact gamepackClean = new Artifact();
    private final Map<String, Certificate[]> classCerts = new HashMap<>();
    private final Map<String, URL> urls = new HashMap<>();

    public Libs(URL[] urls) throws Exception {
        ArtifactReader.read(this, urls);
    }

    public byte[] gamepackByName(String name)
    {
        return gamepack.classes.get(name);
    }

    public byte[] classByName(String className) {
        byte[] classBytes = runelite.classes.get(className);
        if (classBytes != null)
        {
            return classBytes;
        }
        return other.classes.get(className);
    }
}
