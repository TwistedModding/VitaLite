package com.tonic.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@RequiredArgsConstructor
@Getter
public class TileItemEx
{
    private final TileItem item;
    private final WorldPoint worldPoint;
    private final LocalPoint localPoint;
}
