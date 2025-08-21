package com.tonic;

import java.io.File;
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
}
