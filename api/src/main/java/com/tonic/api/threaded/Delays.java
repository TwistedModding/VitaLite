package com.tonic.api.threaded;

import com.tonic.util.Coroutine;
import com.tonic.services.GameCache;

import java.util.function.Supplier;

/**
 * Utility class for handling delays and ticks in the game. (For threaded automation)
 */
public class Delays
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
            if(Thread.currentThread().isInterrupted() || Coroutine._isCancelled())
            {
                throw new RuntimeException();
            }
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

    /**
     * Waits until the specified condition is true.
     * @param condition the condition to be met
     */
    public static void waitUntil(Supplier<Boolean> condition)
    {
        while(!condition.get())
        {
            if(Thread.currentThread().isInterrupted() || Coroutine._isCancelled())
            {
                throw new RuntimeException();
            }
            wait(100);
        }
    }

    /**
     * Waits until the specified condition is true or the timeout is reached.
     * @param condition the condition to be met
     * @param timeoutMS the maximum time to wait in milliseconds
     * @return true if the condition was met, false if the timeout was reached
     */
    public static boolean waitUntil(Supplier<Boolean> condition, long timeoutMS)
    {
        long start = System.currentTimeMillis();
        while(!condition.get())
        {
            if(System.currentTimeMillis() - start > timeoutMS)
            {
                return false;
            }
            if(Thread.currentThread().isInterrupted() || Coroutine._isCancelled())
            {
                throw new RuntimeException();
            }
            wait(100);
        }
        return true;
    }

    /**
     * Waits until the specified condition is true or the timeout is reached.
     * @param condition the condition to be met
     * @param ticks the maximum time to wait in game ticks
     * @return true if the condition was met, false if the timeout was reached
     */
    public static boolean waitUntil(Supplier<Boolean> condition, int ticks)
    {

        int end = GameCache.getTickCount() + ticks;
        while(!condition.get())
        {
            if(GameCache.getTickCount() >= end)
            {
                return false;
            }
            if(Thread.currentThread().isInterrupted() || Coroutine._isCancelled())
            {
                throw new RuntimeException();
            }
            wait(100);
        }
        return true;
    }
}
