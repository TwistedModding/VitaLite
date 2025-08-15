package com.tonic.model;

import com.tonic.Static;
import com.tonic.util.ReflectBuilder;
import lombok.SneakyThrows;

import java.awt.image.BufferedImage;
import java.util.Map;

public class NavButton
{
    private final Object navBuilder;

    public static NavButton builder() {
        try {
            return new NavButton(Static.getRuneLite().getRuneLiteMain());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private NavButton(Class<?> main) throws ClassNotFoundException {
        Class<?> navButtonClass = main.getClassLoader().loadClass("net.runelite.client.ui.NavigationButton");
        navBuilder = ReflectBuilder.of(navButtonClass)
                .staticMethod("builder", null, null)
                .get();
    }

    public NavButton tooltip(String tooltip)
    {
        ReflectBuilder.of(navBuilder)
                .method("tooltip", new Class<?>[]{String.class}, new Object[]{tooltip})
                .get();
        return this;
    }

    public NavButton icon(BufferedImage icon)
    {
        ReflectBuilder.of(navBuilder)
                .method("icon", new Class<?>[]{BufferedImage.class}, new Object[]{icon})
                .get();
        return this;
    }

    public NavButton onClick(Runnable onClick)
    {
        ReflectBuilder.of(navBuilder)
                .method("onClick", new Class<?>[]{Runnable.class}, new Object[]{onClick})
                .get();
        return this;
    }

    public NavButton popup(Map<String, Runnable> popup)
    {
        ReflectBuilder.of(navBuilder)
                .method("popup", new Class<?>[]{Map.class}, new Object[]{popup})
                .get();
        return this;
    }

    public NavButton priority(int priority)
    {
        ReflectBuilder.of(navBuilder)
                .method("priority", new Class<?>[]{int.class}, new Object[]{priority})
                .get();
        return this;
    }

    public Object build() {
        Object button = ReflectBuilder.of(navBuilder)
                .method("build", null, null)
                .get();

        Static.getRuneLite()
                .getClientUI()
                .addNavigation(button);

        return button;
    }
}
