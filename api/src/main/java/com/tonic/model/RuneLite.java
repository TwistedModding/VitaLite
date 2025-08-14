package com.tonic.model;

import com.google.inject.Injector;

import com.tonic.util.ReflectUtil;
import lombok.Getter;

@Getter
public class RuneLite
{
    private final Class<?> runeLiteMain;
    private final Guice injector;
    private final PluginManager pluginManager;
    private final String USER_AGENT;

    public RuneLite(Class<?> runeLiteMain) throws Exception {
        this.runeLiteMain = runeLiteMain;
        this.injector = new Guice((Injector) ReflectUtil.getStaticField(runeLiteMain, "injector"));
        this.pluginManager = new PluginManager(injector);
        this.USER_AGENT = (String) ReflectUtil.getStaticField(runeLiteMain, "USER_AGENT");
    }
}
