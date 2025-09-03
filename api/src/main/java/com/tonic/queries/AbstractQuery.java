package com.tonic.queries;

import com.tonic.Static;
import net.runelite.api.Client;

import java.util.List;
import java.util.function.Predicate;

public abstract class AbstractQuery<T, Q extends AbstractQuery<T, Q>>
{
    protected final List<T> cache;
    protected final Client client;

    public AbstractQuery(List<T> cache) {
        this.cache = cache;
        this.client = Static.getClient();
    }

    protected abstract Q self();

    /**
     * filter by predicate
     * @param predicate condition
     * @return ActorQuery
     */
    public Q removeIf(Predicate<T> predicate)
    {
        cache.removeIf(predicate);
        return self();
    }

    /**
     * filter by predicate
     * @param predicate condition
     * @return ActorQuery
     */
    public Q keepIf(Predicate<T> predicate)
    {
        cache.removeIf(predicate.negate());
        return self();
    }

    /**
     * Get the first element from the current list
     * @return RSActor
     */
    public T first()
    {
        return cache.get(0);
    }

    /**
     * Get the last element from the current list
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
