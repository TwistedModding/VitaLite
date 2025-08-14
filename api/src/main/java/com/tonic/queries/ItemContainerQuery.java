package com.tonic.queries;

import com.tonic.Static;
import com.tonic.data.ShopID;
import net.runelite.api.*;
import org.apache.commons.lang3.ArrayUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ItemContainerQuery<T extends ItemContainer>
{
    private final List<Item> cache;
    private final List<Item> original;

    /**
     * ItemContainerQuery constructor
     * @param itemContainer container
     */
    public ItemContainerQuery(T itemContainer)
    {
        if(itemContainer != null) {
            this.cache = Static.invoke(() ->
                    Arrays.stream(itemContainer.getItems())
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
            this.original = Static.invoke(() -> Arrays.asList(itemContainer.getItems()));
        }
        else
        {
            this.cache = new ArrayList<>();
            this.original = new ArrayList<>();
        }
    }

    /**
     * ItemContainerQuery constructor 2
     * @param inventoryId InventoryID
     */
    public ItemContainerQuery(InventoryID inventoryId)
    {
        ItemContainer itemContainer = Static.invoke(() -> Static.RL_CLIENT.getItemContainer(inventoryId));
        if(itemContainer != null) {
            this.cache = Static.invoke(() ->
                    Arrays.stream(itemContainer.getItems())
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
            this.original = Static.invoke(() -> Arrays.asList(itemContainer.getItems()));
        }
        else
        {
            this.cache = new ArrayList<>();
            this.original = new ArrayList<>();
        }
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
            this.original = new ArrayList<>();
            return;
        }
        ItemContainer itemContainer = Static.invoke(() -> Static.T_CLIENT.getItemContainer(inventoryId.getItemContainerId()));
        if(itemContainer != null) {
            this.cache = Static.invoke(() ->
                    Arrays.stream(itemContainer.getItems())
                            .filter(i -> i.getId() != -1)
                            .collect(Collectors.toList())
            );
            this.original = Static.invoke(() -> Arrays.asList(itemContainer.getItems()));
        }
        else
        {
            this.cache = new ArrayList<>();
            this.original = new ArrayList<>();
        }
    }

    /**
     * filter by item id/noted id
     * @param id item id
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> withId(int id)
    {
        cache.removeIf(o -> o.getId() != id && Static.RL_CLIENT.getItemDefinition(o.getId()).getLinkedNoteId() != id);
        return this;
    }

    /**
     * filter by item name
     * @param name item name
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> withName(String name)
    {
        cache.removeIf(o -> !Static.RL_CLIENT.getItemDefinition(o.getId()).getName().equalsIgnoreCase(name));
        return this;
    }

    public ItemContainerQuery<T> withAction(String action)
    {
        cache.removeIf(o ->
        {
            ItemComposition comp = Static.RL_CLIENT.getItemDefinition(o.getId());
            return Arrays.stream(comp.getInventoryActions()).noneMatch(a -> a != null && a.contains(action));
        });
        return this;
    }

    /**
     * filter by partial name match (case insensitive)
     * @param namePart name partial
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> withPartialName(String namePart)
    {
        cache.removeIf(o -> {
            ItemComposition comp = Static.RL_CLIENT.getItemDefinition(o.getId());
            return comp.getName().toLowerCase().contains(namePart.toLowerCase());
        });
        return this;
    }

    /**
     * remove by predicate
     * @param predicate condition
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> removeIf(Predicate<Item> predicate)
    {
        cache.removeIf(predicate);
        return this;
    }

    /**
     * keep by predicate
     * @param predicate condition
     * @return ItemContainerQuery\<T\>
     */
    public ItemContainerQuery<T> keepIf(Predicate<Item> predicate)
    {
        cache.removeIf(predicate.negate());
        return this;
    }

    /**
     * get current list from query
     * @return list
     */
    public List<Item> collect()
    {
        return cache;
    }

    /**
     * equivalent of findFirst().orElse(null);
     * @return list
     */
    public Item findFirst()
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
        for(Item item : cache)
        {
            count += item.getQuantity();
        }
        return count;
    }
}
