package com.tonic.model;

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

    private Object getClientThread()
    {
        try
        {
            // Get RuneLite instance
            Object runelite = ReflectUtil.getStaticField(main, "rlInstance");

            // Get ClientUI from RuneLite's clientUI field
            Field clientUIField = runelite.getClass().getDeclaredField("clientUI");
            clientUIField.setAccessible(true);
            Object clientUI = clientUIField.get(runelite);

            // Get the clientThreadProvider field from ClientUI (it's private)
            Field providerField = clientUI.getClass().getDeclaredField("clientThreadProvider");
            providerField.setAccessible(true);  // It's private
            Object provider = providerField.get(clientUI);

            // Call get() on the Provider to get the ClientThread instance
            Method getMethod = provider.getClass().getMethod("get");
            getMethod.setAccessible(true);
            Object clientThread = getMethod.invoke(provider);

            System.out.println("Successfully got ClientThread: " + clientThread);
            return clientThread;
        }
        catch (NoSuchFieldException e) {
            System.out.println("Field not found: " + e.getMessage());
        }
        catch (NoSuchMethodException e) {
            System.out.println("Method not found: " + e.getMessage());
        }
        catch (IllegalAccessException e) {
            System.out.println("Access denied: " + e.getMessage());
        }
        catch (InvocationTargetException e) {
            System.out.println("Invocation failed: " + e.getTargetException().getMessage());
        }
        catch (Exception e) {
            System.out.println("Failed to get ClientThread: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void invoke(Runnable runnable)
    {
        if (clientThread == null)
        {
            clientThread = getClientThread();
        }

        if (clientThread != null)
        {
            try
            {
                clientThread.getClass().getMethod("invoke", Runnable.class).invoke(clientThread, runnable);
            }
            catch (Exception e)
            {
                System.out.println("Failed to invoke runnable on ClientThread: " + e.getMessage());
            }
        }
        else
        {
            System.out.println("ClientThread is not available.");
        }
    }
}
