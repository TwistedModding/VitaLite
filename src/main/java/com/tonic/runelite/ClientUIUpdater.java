package com.tonic.runelite;

import com.tonic.Logger;
import com.tonic.Main;
import com.tonic.model.NavButton;
import com.tonic.model.ui.VitaLiteInfoPanel;
import com.tonic.model.ui.VitaLiteOptionsPanel;
import com.tonic.util.ReflectBuilder;
import com.tonic.util.ResourceUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ClientUIUpdater
{
    private static JPanel wrapper;
    private static JScrollPane scrollPane;
    public static void inject()
    {
        if(Main.optionsParser.isIncognito())
            return;
        SwingUtilities.invokeLater(() -> {
            addNavigation();

            Object clientUI = ReflectBuilder.runelite()
                    .staticField("rlInstance")
                    .field("clientUI")
                    .get();

            JFrame frame = ReflectBuilder.of(clientUI)
                    .field("frame")
                    .get();

            JPanel originalContent = ReflectBuilder.of(clientUI)
                    .field("content")
                    .get();

            BufferedImage icon = ResourceUtil.getImage(Main.class, "icon.png");
            frame.setIconImage(icon);
            frame.setTitle("VitaLite");

            wrapper = new JPanel(new BorderLayout());
            JTextPane console = Logger.getConsole();
            scrollPane = new JScrollPane(console);
            scrollPane.setPreferredSize(new Dimension(0, 150));
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(30, 30, 30), 5));

            wrapper.add(originalContent, BorderLayout.CENTER);
            wrapper.add(scrollPane, BorderLayout.SOUTH);

            frame.setContentPane(wrapper);
            frame.revalidate();
            frame.repaint();
        });
    }

    public static void toggleLogger(boolean remove)
    {
        if(wrapper == null || scrollPane == null)
            return;
        SwingUtilities.invokeLater(() -> {
            if(remove)
            {
                wrapper.remove(scrollPane);
            }
            else
            {
                wrapper.add(scrollPane, BorderLayout.SOUTH);
            }
            wrapper.revalidate();
            wrapper.repaint();
        });
    }

    private static void addNavigation()
    {
        BufferedImage icon = ResourceUtil.getImage(Main.class, "icon.png");
        NavButton.builder()
                .icon(icon)
                .priority(1000)
                .tooltip("VitaLite Info")
                .panel(new VitaLiteInfoPanel())
                .addToNavigation();

        BufferedImage settings_icon = ResourceUtil.getImage(Main.class, "settings.png");
        NavButton.builder()
                .icon(settings_icon)
                .priority(999)
                .tooltip("VitaLite Info")
                .panel(VitaLiteOptionsPanel.INSTANCE)
                .addToNavigation();
    }

    /**
     * Patches the splash screen of the client. don't remove, call is injected.
     * @param jFrame SplashScreen jframe
     */
    public static void patchSplashScreen(JFrame jFrame)
    {
        jFrame.setTitle("VitaLite Launcher");
        jFrame.setIconImage(ResourceUtil.getImage(Main.class, "icon.png"));
        jFrame.setBackground(Color.BLACK);
    }
}
