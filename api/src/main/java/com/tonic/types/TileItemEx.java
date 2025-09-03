package com.tonic.types;

import com.tonic.Static;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@RequiredArgsConstructor
@Getter
public class TileItemEx
{
    private final TileItem item;
    private final WorldPoint worldLocation;
    private final LocalPoint localPoint;
    private String[] actions = null;

    public String getName() {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getItemDefinition(item.getId()).getName());
    }

    public int getId() {
        return item.getId();
    }

    public int getQuantity() {
        return item.getQuantity();
    }

    public String[] getActions()
    {
        if(actions != null)
            return actions;
        if(item == null)
            return new String[0];

        return new String[0];
        //todo: export way to get ground actions
//        actions = Static.invoke(() -> {
//            Client client = Static.getClient();
//            ItemComposition itemComp = client.getItemDefinition(item.getId());
//            return itemComp.get();
//        });
//        return actions;
    }

    public int getStorePrice() {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getItemDefinition(item.getId()).getPrice());
    }
}
