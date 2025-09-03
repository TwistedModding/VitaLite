package com.tonic.queries;

import com.google.common.collect.Lists;
import com.tonic.Static;
import com.tonic.util.Location;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public abstract class ActorQuery<T extends Actor> {
    private final List<? extends T> cache;
    private final Client client;

    public ActorQuery(Iterable<? extends T> cache) {
        this.cache = Lists.newArrayList(cache);
        this.client = Static.getClient();
    }

    /**
     * filter by name
     * @param name actor name
     * @return ActorQuery
     */
    public ActorQuery<T> withName(String name)
    {
        cache.removeIf(o -> !name.equalsIgnoreCase(o.getName()));
        return this;
    }

    /**
     * filter by predicate
     * @param predicate condition
     * @return ActorQuery
     */
    public ActorQuery<T> removeIf(Predicate<T> predicate)
    {
        cache.removeIf(predicate);
        return this;
    }

    /**
     * filter by predicate
     * @param predicate condition
     * @return ActorQuery
     */
    public ActorQuery<T> keepIf(Predicate<T> predicate)
    {
        cache.removeIf(predicate.negate());
        return this;
    }

    /**
     * filter by distance
     * @param distance distance
     * @return ActorQuery
     */
    public ActorQuery<T> withinDistance(int distance)
    {
        cache.removeIf(o -> Location.getDistance(client.getLocalPlayer().getWorldLocation(), o.getWorldLocation()) > distance);
        return this;
    }

    /**
     * Get the nearest RSActor from the current list
     * @return RSActor
     */
    public T nearest()
    {
        cache.sort(Comparator.comparing(o -> Location.getDistance(client.getLocalPlayer().getWorldLocation(), o.getWorldLocation())));
        return cache.get(0);
    }

    /**
     * Get the farthest RSActor from the current list
     * @return RSActor
     */
    public T farthest()
    {
        cache.sort(Comparator.comparing(o -> Location.getDistance(client.getLocalPlayer().getWorldLocation(), o.getWorldLocation())));
        Collections.reverse(cache);
        return cache.get(0);
    }

    /**
     * Get the first RSActor from the current list
     * @return RSActor
     */
    public T first()
    {
        return cache.get(0);
    }

    /**
     * Get the last RSActor from the current list
     * @return RSActor
     */
    public T last()
    {
        return cache.get(cache.size() - 1);
    }

    /**
     * get the current list
     * @return list of Actors
     */
    public List<? extends T> collect()
    {
        return cache;
    }
}