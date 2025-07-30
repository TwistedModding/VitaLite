package com.tonic.runelite.model;

import com.tonic.Main;
import com.tonic.util.ReflectUtil;
import lombok.SneakyThrows;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    @SneakyThrows
    public Object loadDefaultPluginConfiguration(Collection<?> plugins)
    {
        return ReflectUtil.getMethod(
                instance,
                "loadDefaultPluginConfiguration",
                new Class<?>[]{ Collection.class },
                new Object[]{ plugins }
        );
    }

    @SneakyThrows
    public Object startPlugin(Object plugin)
    {
        Class<?> pluginClass = Main.CLASSLOADER.forName("net.runelite.client.plugins.Plugin");
        return ReflectUtil.getMethod(
                instance,
                "startPlugin",
                new Class<?>[]{ pluginClass },
                new Object[]{ plugin }
        );
    }
}
