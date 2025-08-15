package com.tonic.model;

import com.tonic.util.ReflectBuilder;
import com.tonic.util.ReflectUtil;
import lombok.SneakyThrows;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public class PluginManager
{
    private final Object instance;
    public PluginManager(Guice injector)
    {
        this.instance = injector.getBinding("net.runelite.client.plugins.PluginManager");
    }

    @SneakyThrows
    public List<?> loadPlugins(List<Class<?>> plugins)
    {
        return (List<?>) ReflectUtil.getMethod(
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
        Class<?> pluginClass = instance.getClass().getClassLoader().loadClass("net.runelite.client.plugins.Plugin");
        return ReflectUtil.getMethod(
                instance,
                "startPlugin",
                new Class<?>[]{ pluginClass },
                new Object[]{ plugin }
        );
    }
}
