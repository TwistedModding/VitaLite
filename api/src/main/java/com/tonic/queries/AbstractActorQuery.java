package com.tonic.queries;

import com.tonic.util.Location;
import net.runelite.api.Actor;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractActorQuery<T extends Actor, Q extends AbstractActorQuery<T, Q>> extends AbstractQuery<T, Q> {
    public AbstractActorQuery(List<T> cache) {
        super(cache);
    }

    /**
     * filter by name
     * @param name actor name
     * @return ActorQuery
     */
    public Q withName(String name)
    {
        cache.removeIf(o -> !name.equalsIgnoreCase(o.getName()));
        return self();
    }

    /**
     * filter by distance
     * @param distance distance
     * @return ActorQuery
     */
    public Q withinDistance(int distance)
    {
        cache.removeIf(o -> Location.getDistance(client.getLocalPlayer().getWorldLocation(), o.getWorldLocation()) > distance);
        return self();
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
}