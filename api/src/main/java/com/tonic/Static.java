package com.tonic;

import com.tonic.api.TClient;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class Static
{
    public static Client RL_CLIENT;
    public static TClient T_CLIENT;
    public static ClientThread CLIENT_THREAD;

    /**
     * invoke on client thread with return
     *
     * @param supplier runnable block
     * @return return value
     */
    public static <T> T invoke(Supplier<T> supplier) {
        if (!RL_CLIENT.isClientThread()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            Runnable runnable = () -> future.complete(supplier.get());
            invoke(runnable);
            return future.join();
        } else {
            return supplier.get();
        }
    }

    public static void invoke(Runnable runnable) {
        if (!RL_CLIENT.isClientThread()) {
            CLIENT_THREAD.invoke(runnable);
        } else {
            runnable.run();
        }
    }
}
