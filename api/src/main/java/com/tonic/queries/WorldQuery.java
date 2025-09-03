package com.tonic.queries;

import com.tonic.Static;
import com.tonic.queries.abstractions.AbstractQuery;
import net.runelite.client.game.WorldService;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.apache.commons.lang3.ArrayUtils;
import java.util.Comparator;
import java.util.Objects;

public class WorldQuery extends AbstractQuery<World, WorldQuery>
{
    public WorldQuery() {
        super(Objects.requireNonNull(Static.getInjector().getInstance(WorldService.class).getWorlds()).getWorlds());
    }

    @Override
    protected WorldQuery self() {
        return this;
    }

    public WorldQuery isF2p()
    {
        return keepIf(w -> !w.getTypes().contains(WorldType.MEMBERS));
    }

    public WorldQuery isP2p()
    {
        return keepIf(w -> w.getTypes().contains(WorldType.MEMBERS));
    }

    public WorldQuery withId(int... id)
    {
        return removeIf(w -> !ArrayUtils.contains(id, w.getId()));
    }

    public WorldQuery withActivity(String activity)
    {
        return keepIf(w -> w.getActivity() != null && w.getActivity().toLowerCase().contains(activity.toLowerCase()));
    }

    public WorldQuery withTypes(WorldType... types)
    {
        return keepIf(w -> w.getTypes() != null && w.getTypes().stream().anyMatch(t -> ArrayUtils.contains(types, t)));
    }

    public WorldQuery withRegion(WorldRegion... region)
    {
        return keepIf(w -> w.getRegion() != null && ArrayUtils.contains(region, w.getRegion()));
    }

    public WorldQuery withPlayerCount(int min, int max)
    {
        return keepIf(w -> w.getPlayers() >= min && w.getPlayers() <= max);
    }

    public WorldQuery withPlayerCount(int count)
    {
        return keepIf(w -> w.getPlayers() == count);
    }

    public WorldQuery sortByIdAsc()
    {
        return sort(Comparator.comparingInt(World::getId));
    }

    public WorldQuery sortByIdDesc()
    {
        return sort(Comparator.comparingInt(World::getId).reversed());
    }

    public WorldQuery sortByPlayerCountAsc()
    {
        return sort(Comparator.comparingInt(World::getPlayers));
    }

    public WorldQuery sortByPlayerCountDesc()
    {
        return sort(Comparator.comparingInt(World::getPlayers).reversed());
    }
}
