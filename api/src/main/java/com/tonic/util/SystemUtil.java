package com.tonic.util;

import java.awt.*;
import java.net.URI;

public class SystemUtil
{
    public static void openURL(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.err.println("Desktop browsing is not supported on this platform");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
