package com.tonic.queries;

import com.tonic.queries.abstractions.AbstractQuery;
import com.tonic.services.GameCache;
import com.tonic.types.TileItemEx;
import com.tonic.util.Location;
import com.tonic.util.TextUtil;
import net.runelite.api.coords.WorldPoint;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.geom.Point2D;

public class TileItemQuery extends AbstractQuery<TileItemEx, TileItemQuery>
{
    public TileItemQuery() {
        super(GameCache.tileItemList());
    }

    @Override
    protected TileItemQuery self() {
        return this;
    }

    public TileItemQuery withId(int... id)
    {
        return removeIf(o -> !ArrayUtils.contains(id, o.getId()));
    }

    public TileItemQuery withName(String name)
    {
        return keepIf(o -> o.getName() != null && o.getName().equalsIgnoreCase(name));
    }

    public TileItemQuery withNames(String... names)
    {
        return keepIf(o -> o.getName() != null && TextUtil.containsIgnoreCase(o.getName(), names));
    }

    public TileItemQuery within(int distance)
    {
        return keepIf(o -> Location.within(client.getLocalPlayer().getWorldLocation(), o.getWorldLocation(), distance));
    }

    public TileItemQuery within(WorldPoint center, int distance)
    {
        return keepIf(o -> Location.within(center, o.getWorldLocation(), distance));
    }

    public TileItemQuery atLocation(WorldPoint location)
    {
        return keepIf(o -> o.getWorldLocation().equals(location));
    }

    public TileItemQuery sortNearest()
    {
        return sortNearest(client.getLocalPlayer().getWorldLocation());
    }

    public TileItemQuery sortNearest(WorldPoint center)
    {
        Point2D point = new Point2D.Double(center.getX(), center.getY());
        return sort((o1, o2) -> {
            Point2D p0 = new Point2D.Double(o1.getWorldLocation().getX(), o1.getWorldLocation().getY());
            Point2D p1 = new Point2D.Double(o2.getWorldLocation().getX(), o2.getWorldLocation().getY());
            return Double.compare(point.distance(p0), point.distance(p1));
        });
    }

    public TileItemQuery sortFurthest()
    {
        return sortFurthest(client.getLocalPlayer().getWorldLocation());
    }

    public TileItemQuery sortFurthest(WorldPoint center)
    {
        Point2D point = new Point2D.Double(center.getX(), center.getY());
        return sort((o1, o2) -> {
            Point2D p0 = new Point2D.Double(o1.getWorldLocation().getX(), o1.getWorldLocation().getY());
            Point2D p1 = new Point2D.Double(o2.getWorldLocation().getX(), o2.getWorldLocation().getY());
            return Double.compare(point.distance(p1), point.distance(p0));
        });
    }
}
