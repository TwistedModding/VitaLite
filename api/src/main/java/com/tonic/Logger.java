package com.tonic;

public class Logger {
    public static void log(String message) {
        System.out.println("[VitaX] " + message);
    }

    public static void error(String message) {
        System.err.println("[VitaX ERROR] " + message);
    }

    public static void debug(String message) {
        if (Boolean.parseBoolean(System.getProperty("debug", "false"))) {
            System.out.println("[VitaX DEBUG] " + message);
        }
    }
}
