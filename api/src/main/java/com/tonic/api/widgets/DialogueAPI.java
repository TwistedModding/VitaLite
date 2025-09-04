package com.tonic.api.widgets;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.api.game.ClientScriptAPI;
import com.tonic.types.WidgetInfoExtended;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import java.util.ArrayList;
import java.util.List;

public class DialogueAPI
{
    public static String getDialogueHeader()
    {
        return Static.invoke(() -> {
            if (WidgetAPI.get(WidgetInfo.DIALOG_NPC_TEXT) != null) {
                return WidgetAPI.get(WidgetInfo.DIALOG_NPC_NAME).getText();
            }
            else if (WidgetAPI.get(WidgetInfo.DIALOG_PLAYER_TEXT) != null) {
                return "Player";
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG_OPTION_OPTION1) != null) {
                return "Select an Option";
            }
            return "UNKNOWN";
        });
    }

    public static String getDialogueText()
    {
        return Static.invoke(() -> {
            if (WidgetAPI.get(WidgetInfo.DIALOG_NPC_TEXT) != null) {
                return WidgetAPI.get(WidgetInfo.DIALOG_NPC_TEXT).getText();
            }
            else if (WidgetAPI.get(WidgetInfo.DIALOG_PLAYER_TEXT) != null) {
                return WidgetAPI.get(WidgetInfo.DIALOG_PLAYER_TEXT).getText();
            }
            else if (WidgetAPI.get(WidgetInfo.DIALOG_SPRITE_TEXT) != null) {
                return WidgetAPI.get(WidgetInfo.DIALOG_SPRITE_TEXT).getText();
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG2_SPRITE_TEXT) != null) {
                return WidgetAPI.get(WidgetInfoExtended.DIALOG2_SPRITE_TEXT).getText();
            }

            else if (WidgetAPI.get(WidgetInfoExtended.MINIGAME_DIALOG_TEXT) != null) {
                return WidgetAPI.get(WidgetInfoExtended.MINIGAME_DIALOG_TEXT).getText();
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG_NOTIFICATION_TEXT) != null) {
                return WidgetAPI.get(WidgetInfoExtended.DIALOG_NOTIFICATION_TEXT).getText();
            }
            return "";
        });
    }

    public static boolean continueDialogue() {
        TClient client = Static.getClient();
        return Static.invoke(() -> {
            if (WidgetAPI.get(WidgetInfoExtended.DIALOG_NPC_CONTINUE) != null) {
                client.getPacketWriter().resumePauseWidgetPacket(WidgetInfoExtended.DIALOG_NPC_CONTINUE.getId(), -1);
                return true;
            }
            else if (WidgetAPI.get(633, 0) != null) {
                client.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(633, 0), -1);
                return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG_PLAYER_CONTINUE) != null) {
                client.getPacketWriter().resumePauseWidgetPacket(WidgetInfoExtended.DIALOG_PLAYER_CONTINUE.getId(), -1);
                return true;
            }
            else if (WidgetAPI.get(WidgetInfo.DIALOG_SPRITE) != null) {
                client.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.DIALOG_SPRITE.getId(), 0);
                return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG2_SPRITE) != null) {
                client.getPacketWriter().resumePauseWidgetPacket(WidgetInfoExtended.DIALOG2_SPRITE_CONTINUE.getId(), -1);
                return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.MINIGAME_DIALOG_CONTINUE) != null) {
                Widget w = WidgetAPI.get(WidgetInfoExtended.MINIGAME_DIALOG_CONTINUE);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                {
                    client.getPacketWriter().resumePauseWidgetPacket(WidgetInfoExtended.MINIGAME_DIALOG_CONTINUE.getId(), -1);
                    return true;
                }
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG_NOTIFICATION_CONTINUE) != null) {
                Widget w = WidgetAPI.get(WidgetInfoExtended.DIALOG_NOTIFICATION_CONTINUE);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                {
                    client.getPacketWriter().resumePauseWidgetPacket(WidgetInfoExtended.DIALOG_NOTIFICATION_CONTINUE.getId(), -1);
                    return true;
                }
            }
            else if (WidgetAPI.get(WidgetInfoExtended.LEVEL_UP_CONTINUE) != null) {
                Widget w = WidgetAPI.get(WidgetInfoExtended.LEVEL_UP_CONTINUE);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                {
                    client.getPacketWriter().resumePauseWidgetPacket(WidgetInfoExtended.LEVEL_UP_CONTINUE.getId(), -1);
                    return true;
                }
            }
            return false;
        });
    }

    public static boolean dialoguePresent() {
        return Static.invoke(() -> {
            if (WidgetAPI.get(WidgetInfoExtended.DIALOG_NPC_CONTINUE) != null) {
                return true;
            }
            else if (WidgetAPI.get(633, 0) != null) {
                return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG_PLAYER_CONTINUE) != null) {
                return true;
            }
            else if (WidgetAPI.get(WidgetInfo.DIALOG_SPRITE) != null) {
                return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG2_SPRITE) != null) {
                return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.MINIGAME_DIALOG_CONTINUE) != null) {
                Widget w = WidgetAPI.get(WidgetInfoExtended.MINIGAME_DIALOG_CONTINUE);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                    return true;
            }
            else if (WidgetAPI.get(WidgetInfoExtended.DIALOG_NOTIFICATION_CONTINUE) != null) {
                Widget w = WidgetAPI.get(WidgetInfoExtended.DIALOG_NOTIFICATION_CONTINUE);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                    return true;
            }
            else if(WidgetAPI.get(WidgetInfoExtended.LEVEL_UP_CONTINUE) != null) {
                Widget w = WidgetAPI.get(WidgetInfoExtended.LEVEL_UP_CONTINUE);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                    return true;
            }

            return WidgetAPI.get(WidgetInfoExtended.DIALOG_OPTION_OPTION1) != null || WidgetAPI.get(WidgetInfo.DIALOG_OPTION_OPTIONS) != null;
        });
    }

    public static void selectOption(int option) {
        resumePause(WidgetInfoExtended.DIALOG_OPTION_OPTION1.getId(), option);
    }

    public static boolean selectOption(String option) {

        return Static.invoke(() -> {
            Widget widget = WidgetAPI.get(WidgetInfoExtended.DIALOG_OPTION_OPTION1);
            if(widget == null)
                return false;
            Widget[] dialogOption1kids = widget.getChildren();
            if(dialogOption1kids == null)
                return false;
            if(dialogOption1kids.length < 2)
                return false;
            int i = 0;
            for(Widget w : dialogOption1kids) {
                if(w.getText().toLowerCase().contains(option.toLowerCase())) {
                    selectOption(i);
                    return true;
                }
                i++;
            }
            return false;
        });
    }

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

    public static void resumePause(int widgetID, int optionIndex) {
        TClient client = Static.getClient();
        Static.invoke(() -> client.getPacketWriter().resumePauseWidgetPacket(widgetID, optionIndex));
    }

    public static void makeX(int quantity)
    {
        TClient client = Static.getClient();
        Static.invoke(() -> {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().resumePauseWidgetPacket(17694734, quantity);
        });
    }

    public static List<String> getOptions() {
        return Static.invoke(() -> {
            List<String> options = new ArrayList<>();
            Widget widget = WidgetAPI.get(WidgetInfoExtended.DIALOG_OPTION_OPTION1);
            if(widget == null)
                return options;
            Widget[] dialogOption1kids = widget.getChildren();
            if(dialogOption1kids == null)
                return options;
            if(dialogOption1kids.length < 2)
                return options;
            boolean skipZero = true;
            for(Widget w : dialogOption1kids) {
                if(skipZero)
                {
                    skipZero = false;
                    continue;
                }
                else if(w.getText().isBlank())
                {
                    continue;
                }
                options.add(w.getText());
            }
            return options;
        });
    }

    public static boolean optionPresent(String option)
    {
        List<String> options = getOptions();
        for(String s : options) {
            if(s.toLowerCase().contains(option.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
