package com.tonic.api;

public interface TPacketWriter
{
    void addNode(TPacketBufferNode node);
    TIsaacCipher getIsaacCipher();
    /**
     * send mouse click
     */
    void clickPacket(int mouseButton, int x, int y);

    /**
     * send a widget action packet
     * @param type 0-9
     * @param widgetId widget id
     * @param childId child id
     * @param itemId item id
     */
    void widgetActionPacket(int type, int widgetId, int childId, int itemId);

    /**
     * Send a resume count dialogue packet
     * @param count count
     */
    void resumeCountDialoguePacket(int count);

    /**
     * send a resume string dialogue packet
     * @param text text
     */
    void resumeStringDialoguePacket(String text);

    /**
     * send a resume obj dialogue packet
     * @param id id
     */
    void resumeObjectDialoguePacket(int id);

    /**
     * continue a pause dialogue
     * @param widgetID widget ID
     * @param optionIndex childID
     */
    void resumePauseWidgetPacket(int widgetID, int optionIndex);

    /**
     * send a walk packet
     * @param x worldX
     * @param y worldY
     * @param ctrl ctrl
     */
    void walkPacket(int x, int y, boolean ctrl);

    /**
     * send a widget on game object packet
     * @param selectedWidgetId selectedWidgetId
     * @param itemId itemId
     * @param slot slot
     * @param identifier identifier
     * @param worldX worldX
     * @param worldY worldY
     * @param run run
     */
    void widgetTargetOnGameObjectPacket(int selectedWidgetId, int itemId, int slot, int identifier, int worldX, int worldY, boolean run);

    /**
     * send widget on npc packet
     * @param identifier identifier
     * @param selectedWidgetId widget id
     * @param itemId item id
     * @param slot slot
     * @param run run
     */
    void widgetTargetOnNpcPacket(int identifier, int selectedWidgetId, int itemId, int slot, boolean run);

    /**
     * send widget on player packet
     * @param identifier identifier
     * @param selectedWidgetId widget id
     * @param itemId item id
     * @param slot slot
     * @param ctrl ctrl
     */
    void widgetTargetOnPlayerPacket(int identifier, int selectedWidgetId, int itemId, int slot, boolean ctrl);

    /**
     * send widget on object packet
     * @param type type
     * @param identifier identifier
     * @param worldX worldX
     * @param worldY worldY
     * @param ctrl ctrl
     */
    void objectActionPacket(int type, int identifier, int worldX, int worldY, boolean ctrl);

    /**
     * send widget on widget packet
     * @param selectedWidgetId selectedWidgetId
     * @param itemId itemId
     * @param slot slot
     * @param targetWidgetId targetWidgetId
     * @param itemId2 itemId2
     * @param slot2 slot2
     */
    void widgetOnWidgetPacket(int selectedWidgetId, int itemId, int slot, int targetWidgetId, int itemId2, int slot2);

    /**
     * send a resume name dialogue packet
     * @param text text
     */
    void resumeNameDialoguePacket(String text);

    /**
     * send a widget on ground item packet
     * @param selectedWidgetId selectedWidgetId
     * @param itemId itemId
     * @param slot slot
     * @param groundItemID groundItemID
     * @param worldX worldX
     * @param worldY worldY
     * @param ctrl ctrl
     */
    void widgetOnGroundItemPacket(int selectedWidgetId, int itemId, int slot, int groundItemID, int worldX, int worldY, boolean ctrl);

    /**
     * send an interface close packet
     */
    void interfaceClosePacket();

    /**
     * send a chat packet
     * @param type type
     * @param text the chat message text
     */
    void chatPacket(int type, String text);

    /**
     * send an item action packet
     * @param slot the slot of the item in the inventory
     * @param id the item id
     * @param action the action to perform on the item
     */
    void itemActionPacket(int slot, int id, int action);

    /**
     * send an item on item packet
     * @param itemId itemId
     * @param slot slot
     * @param itemId2 itemId2
     * @param slot2 slot2
     */
    void itemOnItemPacket(int itemId, int slot, int itemId2, int slot2);

    /**
     * send an item on game object packet
     * @param itemID itemID
     * @param slot slot
     * @param objectID objectID
     * @param worldX worldX
     * @param worldY worldY
     * @param run run
     */
    void itemOnGameObjectPacket(int itemID, int slot, int objectID, int worldX, int worldY, boolean run);

    /**
     * send an item on player packet
     * @param itemId itemId
     * @param slot slot
     * @param playerIndex playerIndex
     * @param run run
     */
    void itemOnPlayerPacket(int itemId, int slot, int playerIndex, boolean run);

    /**
     * send an item on npc packet
     * @param itemId itemId
     * @param slot slot
     * @param npcIndex npcIndex
     * @param run run
     */
    void itemOnNpcPacket(int itemId, int slot, int npcIndex, boolean run);
}
