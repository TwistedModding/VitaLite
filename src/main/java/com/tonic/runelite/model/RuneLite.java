package com.tonic.runelite.model;

import com.google.inject.Injector;
import com.tonic.Main;
import com.tonic.util.ReflectUtil;
import lombok.Getter;
@Getter
public class RuneLite
{
    private final Class<?> runeLiteMain;
    private final Guice injector;
    private final PluginManager pluginManager;

    public RuneLite() throws Exception {
        this.runeLiteMain = Main.CLASSLOADER.getMain();
        this.injector = new Guice((Injector) ReflectUtil.getStaticField(runeLiteMain, "injector"));
        this.pluginManager = new PluginManager(injector);
        System.out.println(runeLiteMain.getName());
    }
}
