package com.tonic.model;

import com.tonic.Logger;
import com.tonic.util.ReflectUtil;

public class RLEventBus
{
    private final Object eventBus;

    RLEventBus(Guice injector) {
        this.eventBus = injector.getBinding("net.runelite.client.eventbus.EventBus");
    }

    public void post(Object event) {
        try
        {
            Class<?> eventClass = event.getClass();
            ReflectUtil.getMethod(eventBus, "post", new Class[]{eventClass}, new Object[]{event});
        }
        catch (Exception e)
        {
            Logger.error("Failed to post event: " + event.getClass().getName());
        }
    }

    public void register(Object listener) {
        try
        {
            Class<?> listenerClass = listener.getClass();
            ReflectUtil.getMethod(eventBus, "register", new Class[]{listenerClass}, new Object[]{listener});
        }
        catch (Exception e)
        {
            Logger.error("Failed to register listener: " + listener.getClass().getName());
        }
    }

    public void unregister(Object listener) {
        try
        {
            Class<?> listenerClass = listener.getClass();
            ReflectUtil.getMethod(eventBus, "unregister", new Class[]{listenerClass}, new Object[]{listener});
        }
        catch (Exception e)
        {
            Logger.error("Failed to unregister listener: " + listener.getClass().getName());
        }
    }
}
