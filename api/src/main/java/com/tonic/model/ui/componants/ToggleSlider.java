package com.tonic.model.ui.componants;

import javax.swing.*;
import java.awt.*;

public class ToggleSlider extends JToggleButton {
    private static final Color TOGGLE_OFF_BG = new Color(65, 65, 70);
    private static final Color TOGGLE_ON_BG = new Color(64, 169, 211);
    private static final Color TOGGLE_KNOB = new Color(245, 245, 250);
    private static final int TOGGLE_WIDTH = 44;
    private static final int TOGGLE_HEIGHT = 24;
    private float animationProgress = 0f;
    private Timer animator;

    public ToggleSlider() {
        setPreferredSize(new Dimension(TOGGLE_WIDTH, TOGGLE_HEIGHT));
        setMinimumSize(new Dimension(TOGGLE_WIDTH, TOGGLE_HEIGHT));
        setMaximumSize(new Dimension(TOGGLE_WIDTH, TOGGLE_HEIGHT));
        setOpaque(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addActionListener(e -> animateToggle());
    }

    private void animateToggle() {
        if (animator != null && animator.isRunning()) {
            animator.stop();
        }

        final float start = animationProgress;
        final float end = isSelected() ? 1f : 0f;

        final long startTime = System.currentTimeMillis();
        final int duration = 200; // milliseconds

        animator = new Timer(10, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1f, (float)elapsed / duration);

            animationProgress = start + (end - start) * progress;

            if (progress >= 1f) {
                animationProgress = end;
                ((Timer)e.getSource()).stop();
            }
            repaint();
        });

        animator.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw track
        Color trackColor = mixColors(animationProgress);
        g2d.setColor(trackColor);
        g2d.fillRoundRect(0, 0, TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT, TOGGLE_HEIGHT);

        // Draw knob
        int knobSize = TOGGLE_HEIGHT - 6;
        int knobX = (int)(3 + (TOGGLE_WIDTH - knobSize - 6) * animationProgress);
        int knobY = 3;

        // Knob shadow
        g2d.setColor(new Color(0, 0, 0, 50));
        g2d.fillOval(knobX + 1, knobY + 1, knobSize, knobSize);

        // Knob
        g2d.setColor(TOGGLE_KNOB);
        g2d.fillOval(knobX, knobY, knobSize, knobSize);
    }

    private Color mixColors(float ratio) {
        int r = (int)(ToggleSlider.TOGGLE_OFF_BG.getRed() * (1 - ratio) + ToggleSlider.TOGGLE_ON_BG.getRed() * ratio);
        int g = (int)(ToggleSlider.TOGGLE_OFF_BG.getGreen() * (1 - ratio) + ToggleSlider.TOGGLE_ON_BG.getGreen() * ratio);
        int b = (int)(ToggleSlider.TOGGLE_OFF_BG.getBlue() * (1 - ratio) + ToggleSlider.TOGGLE_ON_BG.getBlue() * ratio);
        return new Color(r, g, b);
    }
}
