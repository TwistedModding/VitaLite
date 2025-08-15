package com.tonic.runelite;

import com.tonic.Logger;
import com.tonic.Main;
import com.tonic.util.ReflectBuilder;
import com.tonic.util.ResourceUtil;

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
        });
    }
}
