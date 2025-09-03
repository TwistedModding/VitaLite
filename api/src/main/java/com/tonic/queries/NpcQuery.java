package com.tonic.queries;

import com.tonic.queries.abstractions.AbstractActorQuery;
import com.tonic.services.GameCache;
import net.runelite.api.NPC;

public class NpcQuery extends AbstractActorQuery<NPC, NpcQuery>
{
    public NpcQuery()
    {
        super(GameCache.npcList());
    }

    @Override
    protected NpcQuery self() {
        return this;
    }

    public NpcQuery withIds(int... ids)
    {
        return keepIf(n -> {
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