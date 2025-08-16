package com.tonic.runelite;

import com.tonic.Logger;
import com.tonic.Main;
import com.tonic.Static;
import com.tonic.model.NavButton;
import com.tonic.model.pluginpanel.VitaLiteInfoPanel;
import com.tonic.util.ReflectBuilder;
import com.tonic.util.ResourceUtil;
import com.tonic.util.SystemUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ClientUIUpdater
{
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

            JPanel wrapper = new JPanel(new BorderLayout());
            JTextPane console = Logger.getConsole();
            JScrollPane scrollPane = new JScrollPane(console);
            scrollPane.setPreferredSize(new Dimension(0, 100));
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(30, 30, 30), 5));

            wrapper.add(originalContent, BorderLayout.CENTER);
            wrapper.add(scrollPane, BorderLayout.SOUTH);

            frame.setContentPane(wrapper);
            frame.revalidate();
            frame.repaint();
        });
    }

    private static void addNavigation()
    {
        BufferedImage headless_icon = ResourceUtil.getImage(Main.class, "headless.png");
        NavButton.builder()
                .icon(headless_icon)
                .tooltip("Toggle Headless")
                .onClick(() -> Static.setHeadless(!Static.isHeadless()))
                .addToNavigation();

        BufferedImage icon = ResourceUtil.getImage(Main.class, "icon.png");
        NavButton.builder()
                .icon(icon)
                .priority(1000)
                .tooltip("VitaLite Info")
                .panel(new VitaLiteInfoPanel())
                .addToNavigation();
    }
}
