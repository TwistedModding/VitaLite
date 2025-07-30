package com.tonic.runelite.model;

import com.google.inject.Injector;
import com.tonic.runelite.SimpleClassLoader;
import com.tonic.util.ReflectUtil;
import lombok.Getter;
@Getter
public class RuneLite
{
    private final SimpleClassLoader simpleLoader = new SimpleClassLoader(getClass().getClassLoader());
    private final Class<?> runeLiteMain;
    private final Guice injector;
    private final PluginManager pluginManager;

    public RuneLite(Class<?> runeLiteMain) throws Exception {
        this.runeLiteMain = runeLiteMain;
        this.injector = new Guice((Injector) ReflectUtil.getStaticField(runeLiteMain, "injector"));
        this.pluginManager = new PluginManager(injector);
    }
}
