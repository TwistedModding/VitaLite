package com.tonic.widgets;

import com.tonic.Logger;
import com.tonic.Static;
import com.tonic.queries.ItemContainerQuery;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.WidgetInfo;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Inventory automation class
 */
public class Inventory {

    public static List<Item> getItems()
    {
        return new ItemContainerQuery<>(InventoryID.INVENTORY).collect();
    }
    /**
     * get an Item object by id
     * @param itemId item id
     * @return item
     */
    public static Item getItem(int itemId)
    {
        return new ItemContainerQuery<>(InventoryID.INVENTORY).withId(itemId).findFirst();
    }

    /**
     * get an Item object by id
     * @param itemName item name
     * @return item
     */
    public static Item getItem(String itemName)
    {
        return new ItemContainerQuery<>(InventoryID.INVENTORY).withName(itemName).findFirst();
    }

    /**
     * get an Item object by predicate
     * @param predicate predicate
     * @return item
     */
    public static Item getItem(Predicate<Item> predicate)
    {
        return new ItemContainerQuery<>(InventoryID.INVENTORY).keepIf(predicate).findFirst();
    }

    /**
     * interact with an item
     * @param item item
     * @param action action
     */
    public static void interact(Item item, String action)
    {
        ItemContainer container = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(container == null || item == null)
        {
            Logger.warn("Inventory is null or item is null");
            return;
        }
        int slot = Arrays.asList(container.getItems()).indexOf(item);
        itemAction(slot, item.getId(), getAction(item, action));
    }

    public static void interact(int itemId, String action)
    {
        Item item = getItem(itemId);
        if(item == null)
        {
            Logger.warn("Item not found in inventory: " + itemId);
            return;
        }
        ItemContainer container = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(container == null)
        {
            Logger.warn("Inventory is null");
            return;
        }
        int slot = Arrays.asList(container.getItems()).indexOf(item);
        itemAction(slot, item.getId(), getAction(item, action));
    }

    /**
     * interact with an item
     * @param item item
     * @param action action
     */
    public static void interact(Item item, int action)
    {
        ItemContainer container = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(container == null || item == null)
        {
            Logger.warn("Inventory is null or item is null");
            return;
        }
        int slot = Arrays.asList(container.getItems()).indexOf(item);
        itemAction(slot, item.getId(), action);
    }

    /**
     * interact with an item
     * @param itemId item id
     * @param action action
     */
    public static void interact(int itemId, int action) {
        Item item = getItem(itemId);
        ItemContainer container = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(container == null || item == null)
        {
            Logger.warn("Inventory is null or item is null");
            return;
        }
        int slot = Arrays.asList(container.getItems()).indexOf(item);
        itemAction(slot, item.getId(), action);
    }

    public static void interact(int[] itemIds, int action) {
        for(int itemId : itemIds)
        {
            Item item = getItem(itemId);
            if(item != null) {
                ItemContainer container = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
                if(container == null)
                {
                    Logger.warn("Inventory is null");
                    return;
                }
                int slot = Arrays.asList(container.getItems()).indexOf(item);
                itemAction(slot, item.getId(), action);
                return;
            }
        }
    }

    /**
     * interact with an item
     * @param itemName item name
     * @param action action
     */
    public static void interact(String itemName, int action) {
        Item item = getItem(itemName);
        if(item != null) {
            ItemContainer container = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
            if(container == null)
            {
                Logger.warn("Inventory is null");
                return;
            }
            int slot = Arrays.asList(container.getItems()).indexOf(item);
            itemAction(slot, item.getId(), action);
        }
    }

    private static int getAction(Item item, String option)
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
        ItemComposition comp = Static.RL_CLIENT.getItemDefinition(item.getId());
        if(comp == null)
        {
            Logger.warn("Item composition is null");
            return -1;
        }
        String[] actions = comp.getInventoryActions();
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

    /**
     * interact with an item
     * @param slot slot
     * @param id item id
     * @param action action
     */
    public static void itemAction(int slot, int id, int action) {
        if(id == 6512 || id == -1)
            return;

        Static.invoke(() -> {
            Static.T_CLIENT.getPacketWriter().clickPacket(0, -1, -1);
            Static.T_CLIENT.getPacketWriter().widgetActionPacket(action, WidgetInfo.INVENTORY.getId(), slot, id);
        });
    }

    /**
     * check if your inventory is full
     * @return bool
     */
    public static boolean isFull()
    {
        return getEmptySlots() <= 0;
    }

    /**
     * check if your inventory is empty
     * @return bool
     */
    public static boolean isEmpty()
    {
        return getEmptySlots() == 28;
    }

    /**
     * check how many empty slots you have left
     * @return slots
     */
    public static int getEmptySlots() {
        refreshInventory();
        return Static.invoke(() -> {
            ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
            if (inventory != null) {
                return 28 - (int)Arrays.stream(inventory.getItems())
                        .filter(item -> item != null && item.getId() != -1 && item.getId() != 6512)
                        .count();
            } else {
                return 28;
            }
        });
    }

    /**
     * cs2 stuff sometimes required to get correct information from the inventory
     */
    public static void refreshInventory() {
        Static.invoke(() -> {
//            ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
//            if(inventory == null)
//                return;
//            if(!Bank.isOpen(client))
//            {
//                Static.RL_CLIENT.runScript(6009, 9764864, 28, 1, -1);
//            }
        });
    }

    /**
     * check if your inventory contains an item by id
     * @param itemId item id
     * @return bool
     */
    public static boolean contains(int itemId)
    {
        return Static.invoke(() -> {
            ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
            if(inventory == null)
                return false;

            return Arrays.stream(inventory.getItems()).anyMatch(item -> item != null && item.getId() == itemId);
        });
    }

    public static boolean contains(String name)
    {
        return Static.invoke(() -> {
            ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
            if(inventory == null)
                return false;

            return Arrays.stream(inventory.getItems()).anyMatch(item ->
            {
                if(item != null)
                    return false;
                ItemComposition comp = Static.RL_CLIENT.getItemDefinition(item.getId());
                return comp != null && comp.getName() != null && comp.getName().toLowerCase().contains(name.toLowerCase());
            });
        });
    }

    /**
     * check if your inventory contains all of a list of items by id
     * @param itemIds item ids
     * @return bool
     */
    public static boolean contains(int... itemIds)
    {
        for(int itemId : itemIds)
        {
            if(Inventory.getItem(itemId) == null)
                return false;
        }
        return true;
    }

    /**
     * checks if your inventory contains any of the items supplied
     * @param itemIds item ids
     * @return boolean
     */
    public static boolean containsAny( int... itemIds)
    {
        for(int itemId : itemIds)
        {
            if(Inventory.getItem(itemId) != null)
                return true;
        }
        return false;
    }

    /**
     * check if your inventory contains any one of a list of items by id
     * @param itemIds item ids
     * @return bool
     */
    public static boolean contains(List<Integer> itemIds)
    {
        return Static.invoke(() -> {
            ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
            if(inventory == null)
                return false;
            for(int itemId : itemIds)
            {
                if(Arrays.stream(inventory.getItems()).anyMatch(item -> item != null && item.getId() == itemId))
                    return true;
            }
            return false;
        });
    }

    /**
     * check how many of an item your inventory contains by ids
     * @param itemId item ids
     * @return count
     */
    public static int count(int[] itemId)
    {
        int count = 0;
        for(int id : itemId)
        {
            count += count(id);
        }
        return count;
    }

    /**
     * check how many of an item your inventory contains by id
     * @param itemId item id
     * @return count
     */
    public static int count(int itemId)
    {
        return Static.invoke(() -> {
            ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
            if(inventory == null)
                return -1;

            int count = 0;
            for(Item item : inventory.getItems())
            {
                ItemComposition comp = Static.RL_CLIENT.getItemDefinition(item.getId());
                if(item.getId() != itemId && comp.getLinkedNoteId() != itemId)
                    continue;
                count += Math.max(item.getQuantity(), 1);
            }
            return count;
        });
    }

    /**
     * check how many of an item your inventory contains by name
     * @param itemName item name
     * @return count
     */
    public static int count(String itemName)
    {
        Item item = getItem(itemName);
        if(item == null)
            return 0;
        return count(item.getId());
    }

    /**
     * drop all the items in your inventory
     * @return number of ticks it will take
     */
    public static int dropAll()
    {
        ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(inventory == null)
            return 0;

        int ticks = (int) Math.ceil((double) (28 - getEmptySlots()) / 10);
        for(Item item : inventory.getItems())
        {
            if(item.getId() == -1 || item.getId() == 6512)
                continue;
            Inventory.interact(item, 6);
        }
        return ticks;
    }

    /**
     * drop all items from your inventory by list of ids
     * @param ids item ids to drop
     * @return number of ticks it will take
     */
    public static int dropAll(List<Integer> ids)
    {
        int count = 0;
        for(int id : ids)
        {
            count = dropAll(id);
        }
        return (int) Math.ceil((double) count / 10);
    }

    /**
     * drop all items from your inventory by list of ids
     * @param ids item ids to drop
     * @return number of ticks it will take
     */
    public static int dropAll(int... ids)
    {
        int count = 0;
        for(int id : ids)
        {
            count = dropAll(id);
        }
        return (int) Math.ceil((double) count / 10);
    }

    /**
     * drop all items from your inventory by list of ids
     * @param id item id to drop
     * @return number of ticks it will take
     */
    public static int dropAll(int id)
    {
        ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(inventory == null)
            return 0;

        int count = 0;
        for(Item item : inventory.getItems())
        {
            if(item != null && item.getId() == id)
            {
                count++;
                Inventory.interact(item, 6);
            }
        }
        return (int) Math.ceil((double) count / 10);
    }

    /**
     * drop all items from your inventory by name
     * @param name item to drop
     * @return number of ticks it will take
     */
    public static int dropAll(String name)
    {
        ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(inventory == null)
            return 0;

        int count = 0;
        for(Item item : inventory.getItems())
        {
            if(item == null || item.getId() == -1 || item.getId() == 6512)
                continue;
            ItemComposition comp = Static.RL_CLIENT.getItemDefinition(item.getId());
            if(comp != null && comp.getName() != null && comp.getName().toLowerCase().contains(name.toLowerCase()))
            {
                count++;
                Inventory.interact(item, 6);
            }
        }
        return (int) Math.ceil((double) count / 10);
    }

    /**
     * drop all items from your inventory that dont match the suplied item ids
     * @param ids item ids
     */
    public static int dropAllExcept(List<Integer> ids)
    {
        ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(inventory == null)
            return 0;

        int count = 0;
        for(Item item : inventory.getItems())
        {
            if(item == null || item.getId() == -1 || item.getId() == 6512)
                continue;
            if(!ids.contains(item.getId()))
            {
                count++;
                Inventory.interact(item, 6);
            }
        }
        return (int) Math.ceil((double) count / 10);
    }

    public static int[] serialized()
    {
        ItemContainer inventory = Static.RL_CLIENT.getItemContainer(InventoryID.INVENTORY);
        if(inventory == null)
            return new int[0];
        int[] inv = new int[28];
        for(Item item : inventory.getItems())
        {
            if(item == null || item.getId() == -1 || item.getId() == 6512)
                continue;
            int slot = Arrays.asList(inventory.getItems()).indexOf(item);
            inv[slot] = item.getId();
        }
        return inv;
    }

    /**
     * compares 2 arrays representing your inventory at different times. the array index is the slot and the value is the
     * item ID. Each array has a length of 28. this will find the first occurrence where an index in inv1 has no value
     * set but that same index in inv2 does have a value set, then returns that value.
     * @param inv1 first inventory snapshot
     * @param inv2 second inventory snapshot
     * @return dif
     */
    public static int getNewItemID(int[] inv1, int[] inv2)
    {
        for (int i = 0; i < inv1.length; i++) {
            if (inv1[i] == 0 && inv2[i] != 0) {
                return inv2[i];
            }
        }
        return -1;
    }
}