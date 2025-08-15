package com.tonic.model;

import com.tonic.util.ReflectBuilder;
import com.tonic.util.ReflectUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RLClientThread {
    private final Class<?> main;
    private Object clientThread;

    public RLClientThread(Class<?> main)
    {
        this.main = main;
    }

    public void invokeAtTickEnd(Runnable r)
    {
        invoke(r, "invokeAtTickEnd");
    }

    public void invokeLater(Runnable runnable)
    {
        invoke(runnable, "invokeLater");
    }

    public void invoke(Runnable runnable)
    {
        invoke(runnable, "invoke");
    }

    private void invoke(Runnable runnable, String method)
    {
        if (clientThread == null)
        {
            clientThread = getClientThread();
        }

        if (clientThread != null)
        {
            try
            {
                clientThread.getClass().getMethod(method, Runnable.class).invoke(clientThread, runnable);
            }
            catch (Exception e)
            {
                System.out.println("Failed to " + method + " runnable on ClientThread: " + e.getMessage());
            }
        }
        else
        {
            System.out.println("ClientThread is not available.");
        }
    }

    private Object getClientThread()
    {
        try
        {
            return ReflectBuilder.of(main)
                    .staticField("rlInstance")
                    .field("clientUI")
                    .field("clientThreadProvider")
                    .method("get", null, null)
                    .get();
        }
        catch (Exception e) {
            System.out.println("Failed to get ClientThread: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
