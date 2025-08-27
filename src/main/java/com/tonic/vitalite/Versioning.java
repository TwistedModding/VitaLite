package com.tonic.vitalite;

import com.tonic.VitaLite;
import com.tonic.util.RuneliteConfigUtil;

import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class Versioning
{
    private Versioning() {
        // Utility class - prevent instantiation
    }

    public static String getVitaLiteVersion()
    {
        try {
            final Manifest manifest = new Manifest(VitaLite.class.getClassLoader()
                    .getResourceAsStream("META-INF/MANIFEST.MF"));
            final Attributes attrs = manifest.getMainAttributes();
            final String version = attrs.getValue("Implementation-Version");
            if (version == null)
            {
                System.out.println("Could not find manifest, assuming dev environment");
                return RuneliteConfigUtil.getTagValueFromURL("release");
            }
            return version;
        } catch (final IOException e) {
            return "UNKNOWN";
        }
    }

    public static String getLiveRuneliteVersion()
    {
        return RuneliteConfigUtil.getTagValueFromURL("release");
    }

    public static boolean isRunningFromShadedJar() {
        final String jarPath = VitaLite.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        return jarPath.contains("shaded") || jarPath.endsWith(".jar");
    }
}
