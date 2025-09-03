package com.tonic.automation.game;

import com.tonic.services.GameCache;

public class DelaysAPI
{
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
        int tick = GameCache.getTickCount() + ticks;
        int start = GameCache.getTickCount();
        while(GameCache.getTickCount() < tick && GameCache.getTickCount() >= start)
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
}
