package com.tonic.types;

import com.tonic.Static;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@RequiredArgsConstructor
@Getter
public class TileObjectEx
{
    private final TileObject tileObject;
    private String[] actions;

    public int getId() {
        return tileObject.getId();
    }

    public String getName() {
        Client client = Static.getClient();
        return Static.invoke(() -> {
            ObjectComposition composition = client.getObjectDefinition(tileObject.getId());
            if(composition == null)
                return null;
            return composition.getName();
        });
    }

    public String[] getActions() {
        if(actions == null)
        {
            Client client = Static.getClient();
            actions = Static.invoke(() -> {
                ObjectComposition composition = client.getObjectDefinition(tileObject.getId());
                if(composition == null)
                    return new String[]{};
                return composition.getActions();
            });
        }
        return actions;
    }

    public WorldPoint getWorldLocation() {
        return tileObject.getWorldLocation();
    }

    public LocalPoint getLocalLocation() {
        return tileObject.getLocalLocation();
    }
}
