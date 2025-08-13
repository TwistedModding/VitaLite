package com.tonic.api;

public interface TPacketWriter
{
    void addNode(TPacketBufferNode node);
    TIsaacCipher getIsaacCipher();
    /**
     * send mouse click
     */
    void click(int mouseButton, int x, int y);

    /**
     * send a widget action packet
     * @param type 0-9
     * @param widgetId widget id
     * @param childId child id
     * @param itemId item id
     */
    void widgetAction(int type, int widgetId, int childId, int itemId);

    /**
     * Send a resume count dialogue packet
     * @param count count
     */
    void resumeCountDialogue(int count);

    /**
     * send a resume string dialogue packet
     * @param text text
     */
    void resumeStringDialogue(String text);

    /**
     * send a resume obj dialogue packet
     * @param id id
     */
    void resumeObjectDialogue(int id);

    /**
     * continue a pause dialogue
     * @param widgetID widget ID
     * @param optionIndex childID
     */
    void resumePauseWidget(int widgetID, int optionIndex);

    /**
     * send a walk packet
     * @param x worldX
     * @param y worldY
     * @param ctrl ctrl
     */
    void doWalk(int x, int y, boolean ctrl);

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
    void sendWidgetTargetOnGameObjectPacket(int selectedWidgetId, int itemId, int slot, int identifier, int worldX, int worldY, boolean run);

    /**
     * send widget on npc packet
     * @param identifier identifier
     * @param selectedWidgetId widget id
     * @param itemId item id
     * @param slot slot
     * @param run run
     */
    void sendWidgetTargetOnNpcPacket(int identifier, int selectedWidgetId, int itemId, int slot, boolean run);

    /**
     * send widget on player packet
     * @param identifier identifier
     * @param selectedWidgetId widget id
     * @param itemId item id
     * @param slot slot
     * @param ctrl ctrl
     */
    void sendWidgetTargetOnPlayerPacket(int identifier, int selectedWidgetId, int itemId, int slot, boolean ctrl);

    /**
     * send widget on object packet
     * @param type type
     * @param identifier identifier
     * @param worldX worldX
     * @param worldY worldY
     * @param ctrl ctrl
     */
    void sendObjectActionPacket(int type, int identifier, int worldX, int worldY, boolean ctrl);

    /**
     * send widget on widget packet
     * @param selectedWidgetId selectedWidgetId
     * @param itemId itemId
     * @param slot slot
     * @param targetWidgetId targetWidgetId
     * @param itemId2 itemId2
     * @param slot2 slot2
     */
    void sendWidgetOnWidgetPacket(int selectedWidgetId, int itemId, int slot, int targetWidgetId, int itemId2, int slot2);

    /**
     * send a resume name dialogue packet
     * @param text text
     */
    void resumeNameDialogue(String text);

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
    void sendWidgetOnGroundItemPacket(int selectedWidgetId, int itemId, int slot, int groundItemID, int worldX, int worldY, boolean ctrl);

    /**
     * send a interface close packet
     */
    void sendInterfaceClosePacket();
}
