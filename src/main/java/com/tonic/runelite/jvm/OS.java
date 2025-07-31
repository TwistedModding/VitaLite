package com.tonic.runelite.jvm;

import javax.annotation.Nonnull;

public class OS
{
    public enum OSType
    {
        Windows, MacOS, Linux, Other
    }

    private static final OSType DETECTED_OS;

    static
    {
        final String os = System
                .getProperty("os.name", "generic")
                .toLowerCase();
        DETECTED_OS = parseOs(os);
    }

    static OSType parseOs(@Nonnull String os)
    {
        os = os.toLowerCase();
        if ((os.contains("mac")) || (os.contains("darwin")))
        {
            return OSType.MacOS;
        }
        else if (os.contains("win"))
        {
            return OSType.Windows;
        }
        else if (os.contains("linux"))
        {
            return OSType.Linux;
        }
        else
        {
            return OSType.Other;
        }
    }

    public static OSType getOs()
    {
        return DETECTED_OS;
    }
}
