package com.tonic.api;

public interface TClient
{
    TPacketWriter getPacketWriter();
    TClientPacket newClientPacket(int id, int length);
    TPacketBufferNode getPacketBufferNode(TClientPacket clientPacket, TIsaacCipher isaacCipher);
}
