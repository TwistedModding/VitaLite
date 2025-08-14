package com.tonic.api;

public interface TClient
{
    TPacketWriter getPacketWriter();
    TClientPacket newClientPacket(int id, int length);
    TPacketBufferNode getPacketBufferNode(TClientPacket clientPacket, TIsaacCipher isaacCipher);
    //ItemContainer getItemContainer(int inventoryId);
    int getTickCount();
    void setVarbit(int varbit, int value);
    int getVarbitValue(int[] varps, int varbitId);
    void setVarbitValue(int[] varps, int varbit, int value);
    int getVarpValue(int varpId);
    boolean isClientThread();
}
