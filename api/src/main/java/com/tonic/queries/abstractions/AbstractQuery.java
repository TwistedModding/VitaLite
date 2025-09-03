package com.tonic.queries.abstractions;

import com.tonic.Static;
import net.runelite.api.Client;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public abstract class AbstractQuery<T, Q extends AbstractQuery<T, Q>>
{
    protected final List<T> cache;
    protected final Client client;
    private final Random random = new Random();

    public AbstractQuery(List<T> cache) {
        this.cache = cache;
        this.client = Static.getClient();
    }

    protected final Q self()
    {
        return (Q) this;
    }

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

    public Q sort(Comparator<T> comparator)
    {
        cache.sort(comparator);
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

    public T random()
    {
        return cache.get(random.nextInt(cache.size()));
    }

    /**
     * get the current list
     * @return list of Actors
     */
    public List<T> collect()
    {
        return cache;
    }

    public int count()
    {
        return cache.size();
    }

    public boolean isEmpty()
    {
        return cache.isEmpty();
    }
}
