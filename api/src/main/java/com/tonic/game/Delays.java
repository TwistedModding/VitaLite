package com.tonic.game;

import com.tonic.Static;
import com.tonic.api.TClient;
import net.runelite.api.Client;

import java.util.function.Supplier;

public class Delays {
    /**
     * sleeps the thread for x1 tick
     */
    public static void tick()
    {
        tick(1);
    }
    /**
     * sleeps the thread for x ticks
     * @param ticks ticks
     */
    public static void tick(int ticks)
    {
        TClient client = Static.T_CLIENT;
        int tick = client.getTickCount() + ticks;
        int start = client.getTickCount();
        while(client.getTickCount() < tick && client.getTickCount() >= start)
        {
            wait(20);
        }
    }

    /**
     * sleep for a defined duration in milliseconds
     * @param ms milliseconds
     */
    public static void wait(int ms)
    {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    public static void waitUntil(Supplier<Boolean> condition)
    {
        while(!condition.get())
        {
            wait(100);
        }
    }

    public static boolean waitUntil(Supplier<Boolean> condition, int ticks)
    {
        TClient client = Static.T_CLIENT;
        int end = client.getTickCount() + ticks;
        while(!condition.get())
        {
            if(client.getTickCount() >= end)
            {
                return false;
            }
            wait(100);
        }
        return true;
    }
}
