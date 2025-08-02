package com.tonic.remap.util;

/**
 * Simple CLI progress bar with long total support.
 */
public class ProgressBar {
    private final long total;
    private final int barLength;
    private long lastRenderedPercent = -1;

    public ProgressBar(long total, int barLength) {
        this.total = Math.max(1, total);
        this.barLength = barLength;
        update(0);
    }

    /**
     * Update the bar to the given progress.
     * @param current current progress (can exceed total)
     */
    public void update(long current) {
        if (current < 0) {
            current = 0;
        }
        if (current > total) {
            current = total;
        }

        double ratio = (double) current / total;
        int filledLength = (int) (ratio * barLength);
        int percent = (int) (ratio * 100);

        if (percent == lastRenderedPercent && current != total) {
            return; // throttle redundant renders
        }
        lastRenderedPercent = percent;

        String bar = "=".repeat(Math.max(0, filledLength)) +
                " ".repeat(Math.max(0, barLength - filledLength));

        System.out.print("\rProgress: [" + bar + "] " + percent + "%");

        if (current == total) {
            System.out.print("\n");
        }
    }
}
