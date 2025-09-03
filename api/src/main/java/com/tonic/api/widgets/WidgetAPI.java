package com.tonic.api.widgets;

import com.tonic.Static;
import com.tonic.api.TClient;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

public class WidgetAPI
{
    /**
     * invoke a widget packet
     * @param action action type
     * @param widgetId packed widget ID
     * @param childId child ID
     * @param itemId item ID
     */
    public static void interact(int action, int widgetId, int childId, int itemId)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetActionPacket(action, widgetId, childId, itemId);
        });
    }

    /**
     * invoke a widget packet
     * @param action action type
     * @param widgetInfo widget info
     * @param childId child ID
     * @param itemId item ID
     */
    public static void interact(int action, WidgetInfo widgetInfo, int childId, int itemId)
    {
        interact(action, widgetInfo.getId(), childId, itemId);
    }

    public static void interact(int action, WidgetInfo widgetInfo, int childId)
    {
        interact(action, widgetInfo.getId(), childId, -1);
    }

    public static void interact(int action, int widgetId, int childId)
    {
        interact(action, widgetId, childId, -1);
    }

    public static void onTileObject(int selectedWidgetId, int itemId, int slot, int objectID, int worldX, int worldY, boolean ctrl)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetTargetOnGameObjectPacket(selectedWidgetId, itemId, slot, objectID, worldX, worldY, ctrl);
        });
    }

    public static void onGroundItem(int selectedWidgetId, int itemId, int slot, int groundItemID, int worldX, int worldY, boolean ctrl)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetOnGroundItemPacket(selectedWidgetId, itemId, slot, groundItemID, worldX, worldY, ctrl);
        });
    }

    public static void onNpc(int selectedWidgetId, int itemId, int slot, int npcIndex, boolean ctrl)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetTargetOnNpcPacket(npcIndex, selectedWidgetId, itemId, slot, ctrl);
        });
    }

    public static void onPlayer(int selectedWidgetId, int itemId, int slot, int playerIndex, boolean ctrl)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetTargetOnPlayerPacket(playerIndex, selectedWidgetId, itemId, slot, ctrl);
        });
    }

    public static void onWidget(int selectedWidgetId, int itemId, int slot, int targetWidgetId, int itemId2, int slot2)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().widgetOnWidgetPacket(selectedWidgetId, itemId, slot, targetWidgetId, itemId2, slot2);
        });
    }

    public static String getText(WidgetInfo widgetInfo)
    {
        return getText(widgetInfo.getGroupId(), widgetInfo.getChildId());
    }

    public static String getText(int groupId, int childId)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> {
            Widget widget = client.getWidget(groupId, childId);
            if(widget == null || widget.getText() == null)
                return "";

            return widget.getText();
        });
    }

    public static Widget get(WidgetInfo info)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getWidget(info));
    }

    public static Widget get(int groupId, int childId)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getWidget(groupId, childId));
    }

    public static Widget get(int groupId, int childId, int child)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> {
            Widget parent = client.getWidget(groupId, childId);
            if(parent == null || parent.getChildren() == null)
                return null;
            return parent.getChild(child);
        });
    }

    public static boolean isVisible(Widget widget)
    {
        return widget != null && !widget.isHidden() && !widget.isSelfHidden();
    }

    public static boolean isVisible(int groupId, int childId)
    {
        return isVisible(get(groupId, childId));
    }
}
