package com.tonic.types;

import com.tonic.Static;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

@Getter
public enum GrandExchangeSlot
{
    SLOT_1(30474247, 1, WidgetInfoExtended.GRAND_EXCHANGE_OFFER1),
    SLOT_2(30474248, 2, WidgetInfoExtended.GRAND_EXCHANGE_OFFER2),
    SLOT_3(30474249, 3, WidgetInfoExtended.GRAND_EXCHANGE_OFFER3),
    SLOT_4(30474250, 4, WidgetInfoExtended.GRAND_EXCHANGE_OFFER4),
    SLOT_5(30474251, 5, WidgetInfoExtended.GRAND_EXCHANGE_OFFER5),
    SLOT_6(30474252, 6, WidgetInfoExtended.GRAND_EXCHANGE_OFFER6),
    SLOT_7(30474253, 7, WidgetInfoExtended.GRAND_EXCHANGE_OFFER7),
    SLOT_8(30474254, 8, WidgetInfoExtended.GRAND_EXCHANGE_OFFER8);

    private final int id;

    private final int slot;

    private final int buyChild = 3;

    private final int sellChild = 4;

    private final WidgetInfoExtended info;

    GrandExchangeSlot(int id, int slot, WidgetInfoExtended info)
    {
        this.id = id;
        this.slot = slot;
        this.info = info;
    }

    public boolean isDone()
    {
        Client client = Static.getClient();
        return Static.invoke(() ->{
            Widget widget = client.getWidget(info.getGroupId(), info.getChildId());
            if(widget == null)
            {
                return false;
            }
            Widget child = widget.getChild(22);
            if(child == null || child.isHidden())
            {
                return false;
            }
            return Integer.toString(child.getTextColor(), 16).equals("5f00");
        });
    }

    public static GrandExchangeSlot getBySlot(int slot)
    {
        for(GrandExchangeSlot s : GrandExchangeSlot.values()) {
            if(s.getSlot() == slot) {
                return s;
            }
        }
        return null;
    }
}