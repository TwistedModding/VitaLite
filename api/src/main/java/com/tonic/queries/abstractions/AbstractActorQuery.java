package com.tonic.queries.abstractions;

import com.tonic.util.Location;
import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractActorQuery<T extends Actor, Q extends AbstractActorQuery<T, Q>> extends AbstractQuery<T, Q>
{
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
    public Q within(int distance)
    {
        cache.removeIf(o -> Location.getDistance(client.getLocalPlayer().getWorldLocation(), o.getWorldLocation()) > distance);
        return self();
    }

    public Q within(WorldPoint center, int distance)
    {
        return keepIf(o -> Location.within(center, o.getWorldLocation(), distance));
    }

    public Q atLocation(WorldPoint location)
    {
        return keepIf(o -> o.getWorldLocation().equals(location));
    }

    public Q sortNearest()
    {
        return sortNearest(client.getLocalPlayer().getWorldLocation());
    }

    public Q sortNearest(WorldPoint center)
    {
        Point2D point = new Point2D.Double(center.getX(), center.getY());
        return sort((o1, o2) -> {
            Point2D p0 = new Point2D.Double(o1.getWorldLocation().getX(), o1.getWorldLocation().getY());
            Point2D p1 = new Point2D.Double(o2.getWorldLocation().getX(), o2.getWorldLocation().getY());
            return Double.compare(point.distance(p0), point.distance(p1));
        });
    }

    public Q sortFurthest()
    {
        return sortFurthest(client.getLocalPlayer().getWorldLocation());
    }

    public Q sortFurthest(WorldPoint center)
    {
        Point2D point = new Point2D.Double(center.getX(), center.getY());
        return sort((o1, o2) -> {
            Point2D p0 = new Point2D.Double(o1.getWorldLocation().getX(), o1.getWorldLocation().getY());
            Point2D p1 = new Point2D.Double(o2.getWorldLocation().getX(), o2.getWorldLocation().getY());
            return Double.compare(point.distance(p1), point.distance(p0));
        });
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