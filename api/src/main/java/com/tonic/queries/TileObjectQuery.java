package com.tonic.queries;

import com.tonic.services.GameCache;
import net.runelite.api.TileObject;

public class TileObjectQuery<T extends TileObject> extends AbstractQuery<TileObject, TileObjectQuery<T>>
{
    public TileObjectQuery()
    {
        super(GameCache.objectList());
    }

    @Override
    protected TileObjectQuery<T> self() {
        return this;
    }
}
