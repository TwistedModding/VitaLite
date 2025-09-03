package com.tonic.api.widgets;

import com.tonic.Logger;
import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.api.game.ClientScriptAPI;
import com.tonic.types.GrandExchangeSlot;
import com.tonic.types.ItemEx;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.WidgetInfo;

public class GrandExchangeAPI
{
    public static void bypassHighOfferWarning()
    {
        Client client = Static.getClient();
        TClient tclient = Static.getClient();
        Static.invoke(() -> {
            if(client.getWidget(289, 8) != null && !client.getWidget(289, 8).isHidden())
            {
                tclient.getPacketWriter().clickPacket(0, -1, -1);
                tclient.getPacketWriter().resumeCountDialoguePacket(1);
            }
        });
    }

    public static int freeSlot()
    {
        try
        {
            Client client = Static.getClient();
            GrandExchangeOffer[] offers = Static.invoke(client::getGrandExchangeOffers);
            for (int slot = 0; slot < 8; slot++) {
                if (offers[slot] == null || offers[slot].getState() == GrandExchangeOfferState.EMPTY)
                {
                    return slot+1;
                }
            }
        }
        catch (Exception e) {
            Logger.error(e);
        }
        return -1;
    }

    public static GrandExchangeSlot startBuyOffer(int itemId, int amount, int price)
    {
        int slotNumber = freeSlot();
        if(slotNumber == -1)
            return null;
        GrandExchangeSlot slot = GrandExchangeSlot.getBySlot(slotNumber);
        if(slot == null)
            return null;
        TClient client = Static.getClient();
        Static.invoke(() -> {
            WidgetAPI.interact(1, slot.getId(), slot.getBuyChild(), -1);
            client.getPacketWriter().resumeObjectDialoguePacket(itemId);
            WidgetAPI.interact(1, 30474266, 12, -1);
            client.getPacketWriter().resumeCountDialoguePacket(price);
            WidgetAPI.interact(1, 30474266, 7, -1);
            client.getPacketWriter().resumeCountDialoguePacket(amount);
            WidgetAPI.interact(1, 30474270, -1, -1);
        });
        ClientScriptAPI.closeNumericInputDialogue();
        return slot;
    }

    public static int startSellOffer(int itemId, int amount, int price)
    {
        int slotNumber = freeSlot();
        if(slotNumber == -1)
            return slotNumber;
        GrandExchangeSlot slot = GrandExchangeSlot.getBySlot(slotNumber);
        if(slot == null)
            return -1;
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().widgetActionPacket(1, slot.getId(), slot.getSellChild(), 65535);
            client.getPacketWriter().widgetActionPacket(1, WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER.getId(), getItemSlot(itemId), itemId);

            client.getPacketWriter().widgetActionPacket(1, 30474266, 12, 65535);
            client.getPacketWriter().resumeCountDialoguePacket(price);
            if(amount != -1)
            {
                client.getPacketWriter().widgetActionPacket(1, 30474266, 7, 65535);
                client.getPacketWriter().resumeCountDialoguePacket(amount);
            }
            client.getPacketWriter().widgetActionPacket(1, 30474270, 65535, 65535);
            client.getPacketWriter().resumeCountDialoguePacket(1);
        });
        ClientScriptAPI.closeNumericInputDialogue();
        return slotNumber;
    }

    public static void collectFromSlot(int slotNumber, boolean noted, int amount)
    {
        GrandExchangeSlot slot = GrandExchangeSlot.getBySlot(slotNumber);
        if(slot == null)
            return;
        TClient client = Static.getClient();
        Static.invoke(() -> {
            int n = noted ? 1 : 2;
            if(amount == 1)
            {
                n = noted ? 2 : 1;
            }
            client.getPacketWriter().widgetActionPacket(1, slot.getId(), 2, 65535);
            client.getPacketWriter().widgetActionPacket(n, 30474264, 2, 2572);
            client.getPacketWriter().widgetActionPacket(1, 30474264, 3, 995);
        });
    }

    public static int getItemSlot(int id)
    {
        ItemEx item = InventoryAPI.getItem(id);
        return item.getSlot();
    }
}
