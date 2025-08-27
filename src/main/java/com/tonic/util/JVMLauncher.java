package com.tonic.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JVMLauncher {

    public static Process launchInNewJVM(String mainClass, String classpath, String[] programArgs) throws IOException {
        List<String> command = new ArrayList<>();

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        command.add(javaBin);
        command.add("-XX:+DisableAttachMechanism");
        command.add("-Drunelite.launcher.blacklistedDlls=RTSSHooks.dll,RTSSHooks64.dll,NahimicOSD.dll,NahimicMSIOSD.dll,Nahimic2OSD.dll,Nahimic2DevProps.dll,k_fps32.dll,k_fps64.dll,SS2DevProps.dll,SS2OSD.dll,GTIII-OSD64-GL.dll,GTIII-OSD64-VK.dll,GTIII-OSD64.dll");
        if(Arrays.asList(programArgs).contains("-noPlugins") || Arrays.asList(programArgs).contains("-min"))
        {
            if(Arrays.asList(programArgs).contains("-min"))
            {
                command.add("-Xmx300m");
                command.add("-Xms32m");
            }
            else
            {
                command.add("-Xmx512m");
                command.add("-Xms64m");
            }
            command.add("-XX:MetaspaceSize=64m");
            command.add("-XX:MaxMetaspaceSize=128m");
        }
        else
        {
            command.add("-Xmx768m");
        }
        command.add("-Xss2m");
        command.add("-XX:CompileThreshold=1500");
        command.add("-XX:+UseSerialGC");
        command.add("-XX:+UseStringDeduplication");

        if (classpath != null && !classpath.isEmpty()) {
            command.add("-cp");
            command.add(classpath);
        } else {
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
        }

        command.add(mainClass);

        command.addAll(Arrays.asList(programArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();

        return pb.start();
    }
}