package com.tonic.automation.widgets;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.queries.InventoryQuery;
import com.tonic.types.ItemEx;
import com.tonic.types.ShopID;
import net.runelite.api.MenuAction;

public class ShopAPI
{
    private static int SHOP_ID = 19660816;

    public static void buy1(int itemId)
    {
        ItemEx item = getShopItem(itemId);
        if(item == null)
            return;
        buy_1(item);
    }

    public static void buy1(String itemName)
    {
        ItemEx item = getShopItem(itemName);
        if(item == null)
            return;
        buy_1(item);
    }

    public static void buy5(int itemId)
    {
        ItemEx item = getShopItem(itemId);
        if(item == null)
            return;
        buy_5(item);
    }

    public static void buy5(String itemName)
    {
        ItemEx item = getShopItem(itemName);
        if(item == null)
            return;
        buy_5(item);
    }

    public static void buy10(int itemId)
    {
        ItemEx item = getShopItem(itemId);
        if(item == null)
            return;
        buy_10(item);
    }

    public static void buy10(String itemName)
    {
        ItemEx item = getShopItem(itemName);
        if(item == null)
            return;
        buy_10(item);
    }

    public static void buy50(int itemId)
    {
        ItemEx item = getShopItem(itemId);
        if(item == null)
            return;
        buy_50(item);
    }

    public static void buy50(String itemName)
    {
        ItemEx item = getShopItem(itemName);
        if(item == null)
            return;
        buy_50(item);
    }

    /**
     * get an item by its name from the shop container
     * @param itemName item name
     * @return item
     */
    public static ItemEx getShopItem(String itemName)
    {
        return InventoryQuery.fromShopId(ShopID.getCurrent()).withName(itemName).first();
    }

    /**
     * get an item by its id from the shop container
     * @param itemId item id
     * @return item
     */
    public static ItemEx getShopItem(int itemId)
    {
        return InventoryQuery.fromShopId(ShopID.getCurrent()).withId(itemId).first();
    }

    /**
     * get the shops current quantity of an item
     * @param itemId item id
     * @return quantity
     */
    public static int getStockQuantity(int itemId)
    {
        return InventoryQuery.fromShopId(ShopID.getCurrent()).withId(itemId).getQuantity();
    }

    /**
     * get the shops current quantity of an item
     * @param itemName item name
     * @return quantity
     */
    public static int getStockQuantity(String itemName)
    {
        return InventoryQuery.fromShopId(ShopID.getCurrent()).withName(itemName).getQuantity();
    }

    /**
     * check if a shop currently has an item in stock
     * @param itemId item id
     * @return boolean
     */
    public static boolean shopContains(int itemId)
    {
        return getStockQuantity(itemId) != 0;
    }

    /**
     * check if a shop currently has an item in stock
     * @param itemName item name
     * @return boolean
     */
    public static boolean shopContains(String itemName)
    {
        return getStockQuantity(itemName) != 0;
    }

    /**
     * buy 1 of an item
     * @param item item
     */
    public static void buy_1(ItemEx item)
    {
        interactShop(item, 2);
    }

    /**
     * buy 5 of an item
     * @param item item
     */
    public static void buy_5(ItemEx item)
    {
        interactShop(item, 3);
    }

    /**
     * buy 10 of an item
     * @param item item
     */
    public static void buy_10(ItemEx item)
    {
        interactShop(item, 4);
    }

    /**
     * buy 50 of an item
     * @param item item
     */
    public static void buy_50(ItemEx item)
    {
        interactShop(item, 5);
    }

    /**
     * interactShop with a shop
     * @param item item
     * @param action action
     */
    public static void interactShop(ItemEx item, int action)
    {
        shopAction(item.getId(), item.getSlot(), action);
    }

    /**
     * send a raw shop item menu action
     * @param itemId item id
     * @param slot slot
     * @param action action
     */
    public static void shopAction(int itemId, int slot, int action)
    {
        TClient client = Static.getTClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.invokeMenuAction("", "", action, MenuAction.CC_OP.getId(), slot + 1, SHOP_ID, itemId, -1, -1);
        });
    }
}
