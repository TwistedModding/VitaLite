package com.tonic;

import com.tonic.util.RuneliteConfigUtil;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static com.tonic.util.JVMLauncher.launchInNewJVM;

public class VitaLite {
    public static void main(String[] args) {
        try {
            String liveRlVersion = RuneliteConfigUtil.getTagValueFromURL("release");
            String currentVersion = getVitaLiteVersion();
            if(!currentVersion.equals(liveRlVersion))
            {
                System.err.println("Warning: You are running VitaLite version " + currentVersion + " but the latest version is " + liveRlVersion + ". Please update to the latest version.");
                return;
            }
            System.out.println("VitaLite version " + currentVersion + " is up to date.");
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "-safeLaunch";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            Process p = launchInNewJVM("com.tonic.vitalite.Main", buildFullClasspath(), newArgs);
            //int exitCode = p.waitFor();
            System.out.println("Launched VitaLite in new JVM");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String buildFullClasspath() throws URISyntaxException {
        String currentClasspath = System.getProperty("java.class.path");
        File sourceLocation = new File(VitaLite.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        String myLocation = sourceLocation.getAbsolutePath();
        if (!currentClasspath.contains(myLocation)) {
            return myLocation + File.pathSeparator + currentClasspath;
        }
        return currentClasspath;
    }

    private static String getVitaLiteVersion()
    {
        try {
            Manifest manifest = new Manifest(VitaLite.class.getClassLoader()
                    .getResourceAsStream("META-INF/MANIFEST.MF"));
            Attributes attrs = manifest.getMainAttributes();
            String version = attrs.getValue("Implementation-Version");
            if (version == null)
            {
                System.out.println("Could not find manifest, assuming dev environment");
                return RuneliteConfigUtil.getTagValueFromURL("release");
            }
            return version;
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }
}
