package com.tonic;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

public class Logger {
    @Getter
    private static final JTextArea console;

    static {
        console = new JTextArea(5, 0); // 5 rows initially
        console.setEditable(false);
        console.setBackground(Color.BLACK);
        console.setForeground(Color.GREEN);
        fontFactory(console);
        console.setText("Console output will appear here...\n");
    }
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

    private static void fontFactory(JTextArea console)
    {
        Font consoleFont = null;
        String[] fontNames = {
                "Consolas",           // Windows
                "Menlo",              // macOS
                "DejaVu Sans Mono",   // Linux
                "Liberation Mono",    // Linux
                "Courier New",        // Fallback
                Font.MONOSPACED       // Generic fallback
        };

        for (String fontName : fontNames) {
            Font f = new Font(fontName, Font.PLAIN, 12);
            if (!f.getFamily().equals(Font.DIALOG)) { // Font exists
                consoleFont = f;
                break;
            }
        }

        if (consoleFont != null) {
            console.setFont(consoleFont);
        }
    }
}
