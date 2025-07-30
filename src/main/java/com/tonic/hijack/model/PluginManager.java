package com.tonic.hijack.model;

import com.tonic.util.ReflectUtil;
import lombok.SneakyThrows;

import java.util.List;
import java.util.function.BiConsumer;

public class PluginManager
{
    private final Object instance;
    public PluginManager(Guice injector)
    {
        this.instance = injector.getByClassName("net.runelite.client.plugins.PluginManager");
    }

    @SneakyThrows
    public Object loadPlugins(List<Class<?>> plugins)
    {
        return ReflectUtil.getMethod(
                instance,
                "loadPlugins",
                new Class<?>[]{ List.class, BiConsumer.class },
                new Object[]{ plugins, null }
        );
    }
}
