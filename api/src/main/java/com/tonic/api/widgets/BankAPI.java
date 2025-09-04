package com.tonic.api.widgets;

import com.tonic.Static;
import com.tonic.api.game.VarAPI;
import com.tonic.queries.InventoryQuery;
import com.tonic.types.ItemEx;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetInfo;

public class BankAPI
{
    public static void setX(int amount)
    {
        int withdrawMode = VarAPI.getVar(VarbitID.BANK_QUANTITY_TYPE);
        if(withdrawMode != 3)
        {
            WidgetAPI.interact(1, 786468, -1, -1);
        }

        int xQuantity = VarAPI.getVar(VarbitID.BANK_REQUESTEDQUANTITY);
        if(xQuantity != amount && amount != 1 && amount != 5 && amount != 10 && amount != -1)
        {
            WidgetAPI.interact(2, 786468, -1, -1);
            DialogueAPI.resumeNumericDialogue(amount);
        }
    }

    public static void setWithdrawMode(boolean b) {
        if(b) {
            WidgetAPI.interact(1, 786458, -1, -1);
        }
        else {
            WidgetAPI.interact(1, 786456, -1, -1);
        }
    }

    public static void withdraw(int id, int amount, boolean noted) {
        setWithdrawMode(noted);
        ItemEx item = Static.invoke(() ->
                InventoryQuery.fromInventoryId(InventoryID.BANK).withId(id).first()
        );

        if(item == null)
            return;

        withdrawAction(item.getId(), amount, item.getSlot());
    }

    public static void withdraw(String name, int amount, boolean noted) {
        setWithdrawMode(noted);
        ItemEx item = Static.invoke(() ->
                InventoryQuery.fromInventoryId(InventoryID.BANK).withName(name).first()
        );

        if(item == null)
            return;

        withdrawAction(item.getId(), amount, item.getSlot());
    }

    public static void deposit(int id, int amount) {
        ItemEx item = Static.invoke(() ->
                InventoryQuery.fromInventoryId(InventoryID.INVENTORY).withId(id).first()
        );

        if(item == null)
            return;

        invokeDepositAction(item.getId(), amount, item.getSlot());
    }

    public static void deposit(String name, int amount) {
        ItemEx item = Static.invoke(() ->
                InventoryQuery.fromInventoryId(InventoryID.INVENTORY).withName(name).first()
        );

        if(item == null)
            return;

        invokeDepositAction(item.getId(), amount, item.getSlot());
    }

    public static void withdrawAction(int id, int amount, int slot) {
        setX(amount);
        if(amount == 1) {
            WidgetAPI.interact(2, WidgetInfo.BANK_ITEM_CONTAINER.getId(), slot, id);
        }
        else if(amount == 5) {
            WidgetAPI.interact(3, WidgetInfo.BANK_ITEM_CONTAINER.getId(), slot, id);
        }
        else if(amount == 10) {
            WidgetAPI.interact(4, WidgetInfo.BANK_ITEM_CONTAINER.getId(), slot, id);
        }
        else if(amount == -1) {
            WidgetAPI.interact(6, WidgetInfo.BANK_ITEM_CONTAINER.getId(), slot, id);
        }
        else {
            WidgetAPI.interact(1, WidgetInfo.BANK_ITEM_CONTAINER.getId(), slot, id);
        }
    }

    public static void invokeDepositAction(int id, int amount, int slot) {
        setX(amount);
        if(amount == 1) {
            WidgetAPI.interact(3, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), slot, id);
        }
        else if(amount == 5) {
            WidgetAPI.interact(4, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), slot, id);
        }
        else if(amount == 10) {
            WidgetAPI.interact(5, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), slot, id);
        }
        else if(amount == -1) {
            WidgetAPI.interact(8, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), slot, id);
        }
        else {
            WidgetAPI.interact(1, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), slot, id);
        }
    }

    public static void depositAll() {
        WidgetAPI.interact(1, WidgetInfo.BANK_DEPOSIT_INVENTORY, -1, -1);
    }

    public static void depositEquipment() {
        WidgetAPI.interact(1, WidgetInfo.BANK_DEPOSIT_EQUIPMENT, -1, -1);
    }

    public static boolean isOpen()
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getItemContainer(InventoryID.BANK) != null);
    }

    public static boolean contains(int itemId)
    {
        return InventoryQuery.fromInventoryId(InventoryID.BANK).withId(itemId).first() != null;
    }

    public static boolean contains(String itemName)
    {
        return InventoryQuery.fromInventoryId(InventoryID.BANK).withName(itemName).first() != null;
    }

    public static int count(int itemId)
    {
        return InventoryQuery.fromInventoryId(InventoryID.BANK).withId(itemId).count();
    }

    public static int count(String itemName)
    {
        return InventoryQuery.fromInventoryId(InventoryID.BANK).withName(itemName).count();
    }

    public static void use(int itemId) {
        ItemEx item = Static.invoke(() ->
                InventoryQuery.fromInventoryId(InventoryID.INVENTORY).withId(itemId).first()
        );
        if(item == null)
            return;
        WidgetAPI.interact(9, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), item.getId(), itemId);
    }

    public static void use(String itemName) {
        ItemEx item = Static.invoke(() ->
                InventoryQuery.fromInventoryId(InventoryID.INVENTORY).withName(itemName).first()
        );
        if(item == null)
            return;
        WidgetAPI.interact(9, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), item.getId(), item.getId());
    }
}
