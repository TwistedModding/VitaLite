package com.tonic.queries;

import com.tonic.Static;
import com.tonic.types.ItemContainerEx;
import com.tonic.types.ItemEx;
import com.tonic.types.ShopID;
import net.runelite.api.InventoryID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * ItemContainer Query class
 * @param <T> container
 */
public class ItemContainerQuery<T extends ItemContainerEx> {
    private final List<ItemEx> cache;

    /**
     * ItemContainerQuery constructor
     * @param itemContainer container
     */
    public ItemContainerQuery(T itemContainer)
    {
        if(itemContainer != null)
            this.cache = Static.invoke(() ->
                    itemContainer.getItems().stream()
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
        else
            this.cache = new ArrayList<>();
    }

    /**
     * ItemContainerQuery constructor 2
     * @param inventoryId InventoryID
     */
    public ItemContainerQuery(InventoryID inventoryId)
    {
        ItemContainerEx itemContainer = new ItemContainerEx(inventoryId);
        if(!itemContainer.getItems().isEmpty())
            this.cache = Static.invoke(() ->
                    itemContainer.getItems().stream()
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
        else
            this.cache = new ArrayList<>();
    }

    /**
     * ItemContainerQuery constructor 2
     * @param inventoryId InventoryID
     */
    public ItemContainerQuery(ShopID inventoryId)
    {
        if(inventoryId == null)
        {
            this.cache = new ArrayList<>();
            return;
        }
        ItemContainerEx itemContainer = new ItemContainerEx(inventoryId.getItemContainerId());
        if(!itemContainer.getItems().isEmpty())
            this.cache = Static.invoke(() ->
                    itemContainer.getItems().stream()
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
        else
            this.cache = new ArrayList<>();
    }

    /**
     * filter by item id
     * @param id item id
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> withId(int id)
    {
        cache.removeIf(o -> o.getId() != id);
        return this;
    }

    public ItemContainerQuery<T> withName(String name)
    {
        cache.removeIf(o -> !o.getName().equalsIgnoreCase(name));
        return this;
    }

    /**
     * remove by predicate
     * @param predicate condition
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> removeIf(Predicate<ItemEx> predicate)
    {
        cache.removeIf(predicate);
        return this;
    }

    /**
     * keep by predicate
     * @param predicate condition
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> keepIf(Predicate<ItemEx> predicate)
    {
        cache.removeIf(predicate.negate());
        return this;
    }

    /**
     * get current list from query
     * @return list
     */
    public List<ItemEx> collect()
    {
        return cache;
    }

    /**
     * equivalent of findFirst().orElse(null);
     * @return list
     */
    public ItemEx findFirst()
    {
        if(cache.isEmpty())
            return null;
        return cache.get(0);
    }

    /**
     * get the quantity of all items left in the list
     * @return quantity
     */
    public int getQuantity()
    {
        int count = 0;
        for(ItemEx item : cache)
        {
            count += item.getQuantity();
        }
        return count;
    }
}