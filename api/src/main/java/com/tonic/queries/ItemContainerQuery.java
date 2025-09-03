package com.tonic.queries;

import com.tonic.Static;
import com.tonic.types.ItemContainerEx;
import com.tonic.types.ItemEx;
import com.tonic.types.ShopID;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ItemContainerQuery extends AbstractQuery<ItemEx, ItemContainerQuery>
{
    public static ItemContainerQuery fromContainer(ItemContainerEx itemContainer)
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

        return new ItemContainerQuery(cache);
    }

    public static ItemContainerQuery fromContainer(ItemContainer itemContainer)
    {
        return fromInventoryId(itemContainer.getId());
    }

    public static ItemContainerQuery fromInventoryId(InventoryID inventoryId)
    {
        return fromInventoryId(inventoryId.getId());
    }

    public static ItemContainerQuery fromInventoryId(int inventoryId)
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

        return new ItemContainerQuery(cache);
    }

    public static ItemContainerQuery fromShopId(ShopID inventoryId)
    {
        if(inventoryId == null)
        {
            return new ItemContainerQuery(new ArrayList<>());
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

        return new ItemContainerQuery(cache);
    }

    public ItemContainerQuery(List<ItemEx> cache) {
        super(cache);
    }

    @Override
    protected ItemContainerQuery self() {
        return this;
    }

    public ItemContainerQuery withId(int id)
    {
        return removeIf(o -> o.getId() != id);
    }

    public ItemContainerQuery withName(String name)
    {
        return removeIf(o -> !o.getName().equalsIgnoreCase(name));
    }

    public ItemContainerQuery withNameContains(String namePart)
    {
        return removeIf(o -> !o.getName().toLowerCase().contains(namePart.toLowerCase()));
    }

    public ItemContainerQuery withAction(String action)
    {
        return removeIf(o -> !o.hasAction(action));
    }

    public ItemContainerQuery withActionContains(String actionPart)
    {
        return removeIf(o -> !o.hasActionContains(actionPart));
    }
}
