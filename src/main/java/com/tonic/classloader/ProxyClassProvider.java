package com.tonic.classloader;

import java.util.Map;
public class ProxyClassProvider
{
    public static final String PROXY_CLASS_PACKAGE = "net.runelite.proxies";
    public static final String PROXY_CLASS_PACKAGE_SLASHED = "net/runelite/proxies/";

    public static final Map<String, byte[]> PROXY_CLASSES = Map.of(
            "net.runelite.proxies.PluginPanelProxy", ProxyClassBuilder.builder()
                    .withName("net.runelite.proxies.PluginPanelProxy")
                    .withSuper("net.runelite.client.ui.PluginPanel")
                    .withConstructors("()V", "(Z)V")
                    .build()
    );
}