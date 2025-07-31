package com.tonic.runelite.jvm;

import java.util.LinkedHashMap;
import java.util.Map;

public enum HardwareAccelerationMode
{
    AUTO,
    OFF,
    DIRECTDRAW,
    OPENGL,
    METAL;

    /**
     * Gets list of JVM properties to enable Hardware Acceleration for this mode.
     * See https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html for reference
     * @return list of params
     */
    public Map<String, String> toParams(OS.OSType os)
    {
        final Map<String, String> params = new LinkedHashMap<>();

        switch (this)
        {
            case DIRECTDRAW:
                if (os != OS.OSType.Windows)
                {
                    throw new IllegalArgumentException("Directdraw is only available on Windows");
                }

                params.put("sun.java2d.d3d", "true");
                // The opengl prop overrides the d3d prop, so explicitly disable it
                params.put("sun.java2d.opengl", "false");
                break;
            case OPENGL:
                if (os == OS.OSType.Windows)
                {
                    // I don't think this is necessary, but historically we've had it here anyway
                    params.put("sun.java2d.d3d", "false");
                }
                else if (os == OS.OSType.MacOS)
                {
                    // The metal prop overrides the opengl prop, so explicitly disable it
                    params.put("sun.java2d.metal", "false");
                }

                params.put("sun.java2d.opengl", "true");
                break;
            case OFF:
                if (os == OS.OSType.Windows)
                {
                    params.put("sun.java2d.d3d", "false");
                }
                else if (os == OS.OSType.MacOS)
                {
                    // Prior to 17, the j2d properties are not checked on MacOS and OpenGL is always used. 17 requires
                    // either OpenGL or Metal to be enabled.
                    throw new IllegalArgumentException("Hardware acceleration mode on MacOS must be one of OPENGL or METAL");
                }

                params.put("sun.java2d.opengl", "false");
                // Unix also has sun.java2d.xrender, which defaults to true, but I've never seen it cause problems
                break;
            case METAL:
                if (os != OS.OSType.MacOS)
                {
                    throw new IllegalArgumentException("Metal is only available on MacOS");
                }

                params.put("sun.java2d.metal", "true");
                break;
        }

        return params;
    }

    public static HardwareAccelerationMode defaultMode(OS.OSType osType)
    {
        switch (osType)
        {
            case Windows:
                return HardwareAccelerationMode.DIRECTDRAW;
            case MacOS:
                return HardwareAccelerationMode.OPENGL;
            case Linux:
            default:
                return HardwareAccelerationMode.OFF;
        }
    }
}
