package com.tonic;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static com.tonic.util.JVMLauncher.launchInNewJVM;

public class VitaLite {
    public static void main(String[] args) {
        try {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "-safeLaunch";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            Process p = launchInNewJVM("com.tonic.vitalite.Main", buildFullClasspath(), newArgs);
            int exitCode = p.waitFor();
            System.out.println("Process exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static String buildFullClasspath() throws URISyntaxException {
        // Get current classpath
        String currentClasspath = System.getProperty("java.class.path");

        // Get the location of THIS class's JAR/directory
        File sourceLocation = new File(VitaLite.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

        // If we're running from a JAR, make sure it's in the classpath
        String myLocation = sourceLocation.getAbsolutePath();

        // Check if our location is already in the classpath
        if (!currentClasspath.contains(myLocation)) {
            return myLocation + File.pathSeparator + currentClasspath;
        }

        return currentClasspath;
    }
}
