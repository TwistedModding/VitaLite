package com.tonic.api;

import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarbitComposition;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.annotations.Varp;

public interface TClient
{
    TPacketWriter getPacketWriter();
    TClientPacket newClientPacket(int id, int length);
    TPacketBufferNode getPacketBufferNode(TClientPacket clientPacket, TIsaacCipher isaacCipher);
    ItemContainer getItemContainer(int inventoryId);
    int getTickCount();
    void setVarbit(@Varbit int varbit, int value);
    VarbitComposition getVarbit(int id);
    int getVarbitValue(int[] varps, @Varbit int varbitId);
    void setVarbitValue(int[] varps, @Varbit int varbit, int value);
    int getVarpValue(@Varp int varpId);
}
