package com.tonic.types;

import com.tonic.Static;
import lombok.*;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

@Getter
@RequiredArgsConstructor
public class ItemEx
{
    private final Item item;
    private final int slot;
    private String[] actions = null;

    public int getId() {
        return item.getId();
    }

    public int getLinkedNoteId() {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getItemDefinition(item.getId()).getLinkedNoteId());
    }

    public String getName() {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getItemDefinition(item.getId()).getName());
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
        actions = Static.invoke(() -> {
            Client client = Static.getClient();
            ItemComposition itemComp = client.getItemDefinition(item.getId());
            return itemComp.getInventoryActions();
        });
        return actions;
    }

    public boolean hasAction(String action)
    {
        String[] actions = getActions();
        if(actions == null)
            return false;
        for(String a : actions)
        {
            if(a != null && a.equalsIgnoreCase(action))
                return true;
        }
        return false;
    }

    public boolean hasActionContains(String actionPart)
    {
        String[] actions = getActions();
        if(actions == null)
            return false;
        for(String a : actions)
        {
            if(a != null && a.toLowerCase().contains(actionPart.toLowerCase()))
                return true;
        }
        return false;
    }
}
