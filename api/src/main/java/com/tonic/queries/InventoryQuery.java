package com.tonic.queries;

import com.tonic.Static;
import com.tonic.queries.abstractions.AbstractQuery;
import com.tonic.types.ItemContainerEx;
import com.tonic.types.ItemEx;
import com.tonic.types.ShopID;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InventoryQuery extends AbstractQuery<ItemEx, InventoryQuery>
{
    public static InventoryQuery fromContainer(ItemContainerEx itemContainer)
    {
        List<ItemEx> cache;
        if(itemContainer != null)
            cache = Static.invoke(() ->
                    itemContainer.getItems().stream()
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
        else
            cache = new ArrayList<>();

        return new InventoryQuery(cache);
    }

    public static InventoryQuery fromContainer(ItemContainer itemContainer)
    {
        return fromInventoryId(itemContainer.getId());
    }

    public static InventoryQuery fromInventoryId(InventoryID inventoryId)
    {
        return fromInventoryId(inventoryId.getId());
    }

    public static InventoryQuery fromInventoryId(int inventoryId)
    {
        List<ItemEx> cache;
        ItemContainerEx itemContainer = new ItemContainerEx(inventoryId);
        if(!itemContainer.getItems().isEmpty())
            cache = Static.invoke(() ->
                    itemContainer.getItems().stream()
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
        else
            cache = new ArrayList<>();

        return new InventoryQuery(cache);
    }

    public static InventoryQuery fromShopId(ShopID inventoryId)
    {
        if(inventoryId == null)
        {
            return new InventoryQuery(new ArrayList<>());
        }

        List<ItemEx> cache;
        ItemContainerEx itemContainer = new ItemContainerEx(inventoryId.getItemContainerId());
        if(!itemContainer.getItems().isEmpty())
            cache = Static.invoke(() ->
                    itemContainer.getItems().stream()
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
        else
            cache = new ArrayList<>();

        return new InventoryQuery(cache);
    }

    public InventoryQuery(List<ItemEx> cache) {
        super(cache);
    }

    public InventoryQuery withId(int... id)
    {
        return removeIf(o -> !ArrayUtils.contains(id, o.getId()) && !ArrayUtils.contains(id, o.getLinkedNoteId()));
    }

    public InventoryQuery withName(String name)
    {
        return removeIf(o -> !o.getName().equalsIgnoreCase(name));
    }

    public InventoryQuery withNameContains(String namePart)
    {
        return removeIf(o -> !o.getName().toLowerCase().contains(namePart.toLowerCase()));
    }

    public InventoryQuery withAction(String action)
    {
        return removeIf(o -> !o.hasAction(action));
    }

    public InventoryQuery withActionContains(String actionPart)
    {
        return removeIf(o -> !o.hasActionContains(actionPart));
    }

    public int getQuantity() {
        int count = 0;
        for(ItemEx item : cache)
        {
            count += item.getQuantity();
        }
        return count;
    }
}
