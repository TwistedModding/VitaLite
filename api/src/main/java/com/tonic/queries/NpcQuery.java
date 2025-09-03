package com.tonic.queries;

import com.tonic.Static;
import net.runelite.api.Client;
import net.runelite.api.NPC;

public class NpcQuery extends ActorQuery<NPC>
{
    public NpcQuery()
    {
        super(((Client) Static.getClient()).getTopLevelWorldView().npcs());
    }

    public NpcQuery withIds(int... ids)
    {
        return (NpcQuery) keepIf(n -> {
            for (int id : ids)
            {
                if (n.getId() == id)
                {
                    return true;
                }
            }
            return false;
        });
    }
}