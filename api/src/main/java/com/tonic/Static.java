package com.tonic;

import com.tonic.api.TClient;
import com.tonic.model.RuneLite;

import java.util.Objects;

public class Static
{
    private static Object RL_CLIENT;
    private static TClient T_CLIENT;
    private static Object EVENT_BUS;
    private static RuneLite RL;

    public static <T> T getClient()
    {
        return (T) RL_CLIENT;
    }

    public static TClient getTClient()
    {
        return T_CLIENT;
    }

    public static <T> T getEventBus()
    {
        return (T) EVENT_BUS;
    }

    public static RuneLite getRuneLite()
    {
        return RL;
    }

    public static void set(Object object, String name)
    {
        switch (name)
        {
            case "RL_CLIENT":
                RL_CLIENT = Objects.requireNonNull(object, "RL_CLIENT cannot be null");
                break;
            case "T_CLIENT":
                T_CLIENT = (TClient) Objects.requireNonNull(object, "T_CLIENT cannot be null");
                break;
            case "EVENT_BUS":
                EVENT_BUS = Objects.requireNonNull(object, "EVENT_BUS cannot be null");
                break;
            case "RL":
                RL = (RuneLite) Objects.requireNonNull(object, "RL cannot be null");
                System.out.println("RuneLite instance set: " + RL);
                break;
            default:
                throw new IllegalArgumentException("Unknown class name: " + name);
        }
    }

//    /**
//     * invoke on client thread with return
//     *
//     * @param supplier runnable block
//     * @return return value
//     */
//    public static <T> T invoke(Supplier<T> supplier) {
//        if (!RL_CLIENT.isClientThread()) {
//            CompletableFuture<T> future = new CompletableFuture<>();
//            Runnable runnable = () -> future.complete(supplier.get());
//            invoke(runnable);
//            return future.join();
//        } else {
//            return supplier.get();
//        }
//    }
//
//    public static void invoke(Runnable runnable) {
//        if (!RL_CLIENT.isClientThread()) {
//            CLIENT_THREAD.invoke(runnable);
//        } else {
//            runnable.run();
//        }
//    }
}
