package com.tonic.queries;

import com.tonic.services.GameCache;
import com.tonic.types.ItemEx;
import com.tonic.types.TileItemEx;

import java.util.List;

public class TileItemQuery extends AbstractQuery<TileItemEx, TileItemQuery>
{
    public TileItemQuery() {
        super(GameCache.tileItemList());
    }

    @Override
    protected TileItemQuery self() {
        return null;
    }


}
