package com.tonic.runelite.jvm;

import java.util.LinkedHashMap;
import java.util.Map;

public class JvmParams
{
    public static void set(Map<String, String> properties)
    {
        final Map<String, String> jvmProps = new LinkedHashMap<>();

        final var hardwareAccelMode = HardwareAccelerationMode.defaultMode(OS.getOs());
        jvmProps.putAll(hardwareAccelMode.toParams(OS.getOs()));

        if (OS.getOs() == OS.OSType.MacOS)
        {
            jvmProps.put("apple.awt.application.appearance", "system");
        }

        jvmProps.put("runelite.launcher.version", "2.7.1");
        setJvmParams(jvmProps);
    }
    private static void setJvmParams(final Map<String, String> params)
    {
        for (var entry : params.entrySet())
        {
            System.setProperty(entry.getKey(), entry.getValue());
        }
    }
}
