package com.tonic.api.widgets;

import com.tonic.Logger;
import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.queries.InventoryQuery;
import com.tonic.types.ItemEx;
import net.runelite.api.InventoryID;
import net.runelite.api.widgets.WidgetInfo;

import java.util.List;
import java.util.function.Predicate;

public class InventoryAPI
{
    public static List<ItemEx> getItems()
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.INVENTORY).collect());
    }

    public static ItemEx getItem(int itemId)
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.INVENTORY).withId(itemId).first());
    }

    public static ItemEx getItem(String itemName)
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.INVENTORY).withName(itemName).first());
    }

    public static ItemEx getItem(Predicate<ItemEx> predicate)
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.INVENTORY).keepIf(predicate).first());
    }

    public static void interact(ItemEx item, String action)
    {
        itemAction(item.getSlot(), item.getId(), getAction(item, action));
    }

    public static void interact(int itemId, String action)
    {
        ItemEx item = getItem(itemId);
        if(item == null)
        {
            Logger.warn("Item not found in inventory: " + itemId);
            return;
        }
        itemAction(item.getSlot(), item.getId(), getAction(item, action));
    }

    public static void interact(ItemEx item, int action)
    {
        if(item == null)
            return;
        itemAction(item.getSlot(), item.getId(), action);
    }

    public static void interact(int itemId, int action) {
        ItemEx item = getItem(itemId);
        if(item != null) {
            itemAction(item.getSlot(), item.getId(), action);
        }
    }

    public static void interact(int[] itemIds, int action) {
        for(int itemId : itemIds)
        {
            ItemEx item = getItem(itemId);
            if(item != null) {
                itemAction(item.getSlot(), item.getId(), action);
                return;
            }
        }
    }

    public static void interact(String itemName, int action) {
        ItemEx item = getItem(itemName);
        if(item != null) {
            itemAction(item.getSlot(), item.getId(), action);
        }
    }

    public static void itemAction(int slot, int id, int action) {
        if(id == 6512 || id == -1)
            return;

        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetActionPacket(action, WidgetInfo.INVENTORY.getId(), slot, id);
        });
    }

    private static int getAction(ItemEx item, String option)
    {
        option = option.toLowerCase();
        switch (option)
        {
            case "drop":
                return 7;
            case "examine":
                return 10;
            case "wear":
            case "wield":
            case "equip":
                return 3;
            case "rub":
                return 6;
        }
        String[] actions = item.getActions();
        int index = -1;
        for(int i = 0; i < actions.length; i++)
        {
            if(actions[i] != null && actions[i].toLowerCase().contains(option))
            {
                index = i;
                break;
            }
        }
        return (index < 4) ? index + 2 : index + 3;
    }
}
