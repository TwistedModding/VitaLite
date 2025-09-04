package com.tonic.api.widgets;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.queries.InventoryQuery;
import com.tonic.types.EquipmentSlot;
import com.tonic.types.ItemEx;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InventoryID;

import java.util.List;
import java.util.function.Predicate;

public class EquipmentAPI
{
    public static boolean isEquipped(int itemId)
    {
        return Static.invoke(() -> !InventoryQuery.fromInventoryId(InventoryID.WORN).withId(itemId).collect().isEmpty());
    }

    public static boolean isEquipped(String itemName)
    {
        return Static.invoke(() -> !InventoryQuery.fromInventoryId(InventoryID.WORN).withName(itemName).collect().isEmpty());
    }

    public static boolean isEquipped(Predicate<ItemEx> predicate)
    {
        return Static.invoke(() -> !InventoryQuery.fromInventoryId(InventoryID.WORN).keepIf(predicate).collect().isEmpty());
    }

    public static ItemEx getItem(int itemId)
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.WORN).withId(itemId).first());
    }

    public static ItemEx getItem(String itemName)
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.WORN).withName(itemName).first());
    }

    public static void unEquip(ItemEx item)
    {
        interact(item, 1);
    }

    public static void unEquip(EquipmentSlot slot)
    {
        ItemEx item = fromSlot(slot);
        if(item != null) {
            interact(item, 1);
        }
    }

    public static void equip(int itemId)
    {
        InventoryAPI.interact(itemId, 3);
    }

    public static void interact(ItemEx item, int action)
    {
        itemAction(item.getSlot(), action);
    }

    public static void itemAction(int slot, int action)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetActionPacket(action, EquipmentSlot.findBySlot(slot).getWidgetInfo().getId(), -1, -1);
        });
    }

    public static ItemEx fromSlot(EquipmentSlot slot)
    {
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.WORN).fromSlot(slot.getSlotIdx()).first());
    }

    public static List<ItemEx> getAll(){
        return Static.invoke(() -> InventoryQuery.fromInventoryId(InventoryID.WORN).collect());
    }

    public static void unequipAll(){
        unEquip(EquipmentSlot.AMULET);
        unEquip(EquipmentSlot.BODY);
        unEquip(EquipmentSlot.WEAPON);
        unEquip(EquipmentSlot.LEGS);
        unEquip(EquipmentSlot.SHIELD);
        unEquip(EquipmentSlot.AMMO);
        unEquip(EquipmentSlot.BOOTS);
        unEquip(EquipmentSlot.HEAD);
        unEquip(EquipmentSlot.CAPE);
        unEquip(EquipmentSlot.GLOVES);
        unEquip(EquipmentSlot.RING);
    }
}
