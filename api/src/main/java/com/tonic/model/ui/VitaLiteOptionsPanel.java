package com.tonic.model.ui;

import com.tonic.Logger;
import com.tonic.Static;
import com.tonic.events.PacketSent;
import com.tonic.model.ui.componants.ToggleSlider;
import com.tonic.model.ui.componants.VPluginPanel;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class VitaLiteOptionsPanel extends VPluginPanel {

    public static final VitaLiteOptionsPanel INSTANCE = new VitaLiteOptionsPanel();
    private static final Color BACKGROUND_GRADIENT_START = new Color(45, 45, 50);
    private static final Color BACKGROUND_GRADIENT_END = new Color(35, 35, 40);
    private static final Color ACCENT_COLOR = new Color(64, 169, 211);
    private static final Color ACCENT_GLOW = new Color(64, 169, 211, 30);
    private static final Color TEXT_COLOR = new Color(200, 200, 205);
    private static final Color HEADER_COLOR = new Color(245, 245, 250);
    private static final Color CARD_BACKGROUND = new Color(55, 55, 60);
    private static final Color SEPARATOR_COLOR = new Color(70, 70, 75);

    private ToggleSlider headlessToggle;
    private ToggleSlider logPacketsToggle;

    private VitaLiteOptionsPanel() {
        super(true);

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gradient = new GradientPaint(
                        0, 0, BACKGROUND_GRADIENT_START,
                        0, getHeight(), BACKGROUND_GRADIENT_END
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // Add some top padding
        contentPanel.add(Box.createVerticalStrut(10));

        // Create header panel with glow effect
        JPanel headerPanel = createGlowPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel headerLabel = new JLabel("VitaLite Settings");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        headerLabel.setForeground(HEADER_COLOR);
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(Box.createVerticalStrut(15));
        headerPanel.add(headerLabel);

        JLabel subtitleLabel = new JLabel("Configure your experience");
        subtitleLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        subtitleLabel.setForeground(ACCENT_COLOR);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(headerPanel);
        contentPanel.add(Box.createVerticalStrut(20));
        contentPanel.add(createSeparator());
        contentPanel.add(Box.createVerticalStrut(20));

        // Headless toggle
        headlessToggle = new ToggleSlider();
        contentPanel.add(createToggleOption(
                "Headless Mode",
                "Run without GUI rendering",
                headlessToggle,
                "headless",
                () -> Static.setHeadless(headlessToggle.isSelected())
        ));
        contentPanel.add(Box.createVerticalStrut(12));

        // Log Packets toggle
        logPacketsToggle = new ToggleSlider();
        contentPanel.add(createToggleOption(
                "Log Packets",
                "Enable packet logging for debugging",
                logPacketsToggle,
                "logPackets",
                () -> {}
        ));

        contentPanel.add(Box.createVerticalStrut(20));
        contentPanel.add(createSeparator());
        contentPanel.add(Box.createVerticalStrut(20));

        add(contentPanel);
    }

    private JPanel createToggleOption(String title, String description, ToggleSlider toggle, String settingKey, Runnable onClick) {
        JPanel optionPanel = new JPanel() {
            private boolean isHovered = false;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isHovered) {
                    g2d.setColor(new Color(60, 60, 65));
                } else {
                    g2d.setColor(CARD_BACKGROUND);
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                g2d.setColor(SEPARATOR_COLOR);
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
        };

        optionPanel.setLayout(new BorderLayout(10, 0));
        optionPanel.setOpaque(false);
        optionPanel.setBorder(new EmptyBorder(12, 15, 12, 15));
        optionPanel.setMaximumSize(new Dimension(PANEL_WIDTH - 40, 60));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(HEADER_COLOR);
        textPanel.add(titleLabel);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        descLabel.setForeground(TEXT_COLOR);
        textPanel.add(descLabel);

        optionPanel.add(textPanel, BorderLayout.CENTER);
        optionPanel.add(toggle, BorderLayout.EAST);

        optionPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                //isHovered = true;
                optionPanel.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                //isHovered = false;
                optionPanel.repaint();
            }
        });

        // Add action listener to toggle
        toggle.addActionListener(e -> onClick.run());

        return optionPanel;
    }

    private JPanel createGlowPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw glow effect
                g2d.setColor(ACCENT_GLOW);
                int glowRadius = 20;
                for (int i = glowRadius; i > 0; i--) {
                    float alpha = (float)(glowRadius - i) / glowRadius * 0.3f;
                    g2d.setColor(new Color(64, 169, 211, (int)(alpha * 255)));
                    g2d.fillRoundRect(i/2, i/2, getWidth() - i, getHeight() - i, 15, 15);
                }

                g2d.setColor(CARD_BACKGROUND);
                g2d.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, 10, 10);
            }
        };
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(PANEL_WIDTH - 20, 80));
        return panel;
    }

    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator();
        separator.setForeground(SEPARATOR_COLOR);
        separator.setMaximumSize(new Dimension(PANEL_WIDTH - 60, 1));
        return separator;
    }

    public void onPacketSent(PacketSent event)
    {
        if(!logPacketsToggle.isSelected())
            return;

        String packetInfo = event.toString();

        if(packetInfo.startsWith("[UNKNOWN("))
            return;

        Logger.info(packetInfo);
    }
}