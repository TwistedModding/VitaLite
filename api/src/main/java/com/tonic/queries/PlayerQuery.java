package com.tonic.queries;

import com.tonic.services.GameCache;
import net.runelite.api.Player;

public class PlayerQuery extends AbstractActorQuery<Player, PlayerQuery>
{
    public PlayerQuery() {
        super(GameCache.playerList());
    }

    @Override
    protected PlayerQuery self() {
        return this;
    }
}
