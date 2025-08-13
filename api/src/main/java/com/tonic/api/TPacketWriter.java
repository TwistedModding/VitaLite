package com.tonic.api;

public interface TPacketWriter
{
    void addNode(TPacketBufferNode node);
    TIsaacCipher getIsaacCipher();

    void widgetAction(int type, int widgetId, int childId, int itemId);
    void resumeCountDialogue(int count);
    //void resumePauseWidget(int widgetID, int optionIndex);
    void resumeObjectDialogue(int id);
    void doWalk(int worldX, int worldY, boolean ctrl);
}
