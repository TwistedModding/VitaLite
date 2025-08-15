package com.tonic;

import com.tonic.util.TextUtil;
import lombok.SneakyThrows;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logger for VitaX
 */
@Singleton
public class Logger {
    private static Logger INSTANCE;

    static {
        setInstance();
    }

    public static JTextPane getConsole()
    {
        if(INSTANCE == null)
            return null;
        return INSTANCE.console;
    }

    /**
     * set normal logging
     * @param state state
     */
    public static void setNormal(boolean state)
    {
        if(INSTANCE == null)
            return;
        INSTANCE.normal = state;
    }

    /**
     * set info logging
     * @param state state
     */
    public static void setInfo(boolean state)
    {
        if(INSTANCE == null)
            return;
        INSTANCE.info = state;
    }

    /**
     * set warning logging
     * @param state state
     */
    public static void setWarning(boolean state)
    {
        if(INSTANCE == null)
            return;
        INSTANCE.warning = state;
    }

    /**
     * set error logging
     * @param state state
     */
    public static void setError(boolean state)
    {
        if(INSTANCE == null)
            return;
        INSTANCE.error = state;
    }

    /**
     * for console input
     * @param data data
     */
    public static void console(String data)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._console(data);
    }

    /**
     * for console output
     * @param head header
     * @param body message
     */
    public static void consoleOutput(String head, String body)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._consoleOutput("(" + head + ") ", body);
    }

    /**
     * for console error output
     * @param head header
     * @param body message
     */
    public static void consoleErrorOutput(String head, String body)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._consoleErrorOutput("(" + head + ") ", body);
    }

    /**
     * for normal purposeful logging
     * @param data data
     */
    public static void norm(String data)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._norm(data);
    }

    /**
     * for general diagnostic logging
     * @param data data
     */
    public static void info(String data)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._info(data);
    }

    /**
     * For non fatal warnings
     * @param data data
     */
    public static void warn(String data)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._warn(data);
    }

    /**
     * for fatal errors
     * @param data data
     */
    public static void error(String data)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._error(data);
    }

    /**
     * for fatal errors
     * @param throwable exception
     */
    public static void error(Throwable throwable)
    {
        throwable.printStackTrace();
        if(INSTANCE == null)
            return;
        INSTANCE._error(throwable.getMessage());
    }

    /**
     * for fatal errors
     * @param throwable throwable
     */
    public static void error(Throwable throwable, String message)
    {
        if(INSTANCE == null)
            return;
        INSTANCE._error(message.replace("%e", throwable.getMessage()));
    }

    /**
     * Initial binding for statically stored instance
     */
    public static void setInstance()
    {
        INSTANCE = new Logger();
    }

    private final JTextPane console;

    private boolean info = true;
    private boolean normal = true;
    private boolean warning = true;
    private boolean error = true;
    private final SimpleAttributeSet CONSOLE;
    private final SimpleAttributeSet NORM;
    private final SimpleAttributeSet INFO;
    private final SimpleAttributeSet WARN;
    private final SimpleAttributeSet ERROR;
    private final float SPACING = 1.5f;
    private Logger()
    {
        console = new JTextPane() {{
            setBackground(Color.BLACK);
            setForeground(Color.LIGHT_GRAY);
            setAutoscrolls(true);
            setEditable(false);
            setFont(new Font("Monoid", Font.PLAIN, 14));
        }};
        DefaultCaret caret = (DefaultCaret)console.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        console.setEditable(false);
        console.setBackground(Color.BLACK);
        console.setForeground(Color.GREEN);
        fontFactory(console);

        CONSOLE = new SimpleAttributeSet();
        StyleConstants.setForeground(CONSOLE, Color.LIGHT_GRAY);

        NORM = new SimpleAttributeSet();
        StyleConstants.setForeground(NORM, Color.GREEN);

        INFO = new SimpleAttributeSet();
        StyleConstants.setForeground(INFO, Color.decode("#ADD8E6"));

        WARN = new SimpleAttributeSet();
        StyleConstants.setForeground(WARN, Color.YELLOW);

        ERROR = new SimpleAttributeSet();
        StyleConstants.setForeground(ERROR, Color.RED);
    }

    private static void fontFactory(JTextPane console)
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

    private void stream(String data, SimpleAttributeSet style)
    {
        String timestamp = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "] ";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                StyleConstants.setLineSpacing(style,SPACING);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), timestamp + data + "\n", style);
            }
        });
    }

    @SneakyThrows
    private void _console(String data)
    {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                StyleConstants.setLineSpacing(NORM,SPACING);
                StyleConstants.setLineSpacing(CONSOLE,SPACING);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), "$ ", NORM);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), data + "\n", CONSOLE);
            }
        });
    }

    @SneakyThrows
    private void _consoleOutput(String head, String body)
    {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                StyleConstants.setLineSpacing(NORM,SPACING);
                StyleConstants.setLineSpacing(CONSOLE,SPACING);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), head, INFO);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), body + "\n", CONSOLE);
            }
        });
    }

    @SneakyThrows
    private void _consoleErrorOutput(String head, String body)
    {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                StyleConstants.setLineSpacing(NORM,SPACING);
                StyleConstants.setLineSpacing(CONSOLE,SPACING);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), head, INFO);
                console.getStyledDocument().insertString(console.getStyledDocument().getLength(), body + "\n", ERROR);
            }
        });
    }

    private void _norm(String data)
    {
        if(!normal)
            return;

        stream(data, NORM);
    }

    private void _info(String data)
    {
        if(!info)
            return;

        stream(data, INFO);
    }

    private void _warn(String data)
    {
        if(!warning)
            return;

        stream(data, WARN);
    }

    private void _error(String data)
    {
        if(!error)
            return;

        stream(data, ERROR);
    }
}