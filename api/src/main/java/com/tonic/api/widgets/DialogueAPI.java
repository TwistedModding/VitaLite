package com.tonic.api.widgets;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.api.game.ClientScriptAPI;
import com.tonic.types.WidgetInfoExtended;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

public class DialogueAPI
{
    public static void resumeObjectDialogue(int id)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().resumeObjectDialoguePacket(id);
            ClientScriptAPI.closeNumericInputDialogue();
        });
    }

    public static void resumeNameDialogue(String text)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().resumeNameDialoguePacket(text);
            ClientScriptAPI.closeNumericInputDialogue();
        });
    }

    public static void resumeNumericDialogue(int value)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().resumeCountDialoguePacket(value);
            ClientScriptAPI.closeNumericInputDialogue();
        });
    }

    public static void makeX(int quantity)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().resumePauseWidgetPacket(17694734, quantity);
        });
    }


    public static boolean optionPresent(String option)
    {
        return Static.invoke(() -> {
            Widget widget = WidgetAPI.get(WidgetInfoExtended.DIALOG_OPTION_OPTION1);
            if(widget == null)
                return false;
            Widget[] dialogOption1kids = widget.getChildren();
            if(dialogOption1kids == null)
                return false;
            if(dialogOption1kids.length < 2)
                return false;
            for(Widget w : dialogOption1kids) {
                if(w.getText().toLowerCase().contains(option.toLowerCase())) {
                    return true;
                }
            }
            return false;
        });
    }
}
