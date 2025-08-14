package com.tonic.widgets;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.game.ClientScript;
import com.tonic.game.Delays;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Static API for dialogue interactions
 */
public class Dialogue {
    public static String getDialogueHeader()
    {
        return Static.invoke(() -> {
            Client client = Static.RL_CLIENT;
            if (client.getWidget(WidgetInfo.DIALOG_NPC_TEXT) != null) {
                return client.getWidget(WidgetInfo.DIALOG_NPC_NAME).getText();
            }
            else if (getWidget(InterfaceID.ChatRight.TEXT) != null) {
                return "Player";
            }
            else if (client.getWidget(WidgetID.DIALOG_OPTION_GROUP_ID, 1) != null) {
                return "Select an Option";
            }
            return "UNKNOWN";
        });
    }

    public static String getDialogueText()
    {
        Client client = Static.RL_CLIENT;
        return Static.invoke(() -> {
            if (client.getWidget(WidgetInfo.DIALOG_NPC_TEXT) != null) {
                return client.getWidget(WidgetInfo.DIALOG_NPC_TEXT).getText();
            }
            else if (getWidget(ComponentID.DIALOG_PLAYER_TEXT) != null) {
                return getWidget(ComponentID.DIALOG_PLAYER_TEXT).getText();
            }
            else if (client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT) != null) {
                return client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT).getText();
            }
            else if (client.getWidget(11, 2) != null) {
                return client.getWidget(11, 2).getText();
            }

            else if (client.getWidget(229, 1) != null) {
                return client.getWidget(229, 1).getText();
            }
            else if (client.getWidget(229, 0) != null) {
                return client.getWidget(229, 0).getText();
            }
            return "";
        });
    }

    /**
     * Generic continue any pause dialogue
     * @return true if there was a dialogue to continue
     */
    public static boolean continueDialogue() {
        Client client = Static.RL_CLIENT;
        TClient tClient = Static.T_CLIENT;
        return Static.invoke(() -> {
            if (client.getWidget(WidgetInfo.DIALOG_NPC_CONTINUE) != null) {
                tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.DIALOG_NPC_CONTINUE.getId(), -1);
                return true;
            }
            else if (client.getWidget(633, 0) != null) {
                tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(633, 0), -1);
                return true;
            }
            else if (client.getWidget(217, 5) != null) {
                tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(217, 5), -1);
                return true;
            }
            else if (client.getWidget(WidgetInfo.DIALOG_SPRITE) != null) {
                tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.DIALOG_SPRITE.getId(), 0);
                return true;
            }
            else if (client.getWidget(11, 0) != null) {
                tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(11, 0), -1);
                return true;
            }
            else if (client.getWidget(229, 2) != null) {
                Widget w = client.getWidget(229, 2);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                {
                    tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(229, 2), -1);
                    return true;
                }
            }
            else if (client.getWidget(229, 1) != null) {
                Widget w = client.getWidget(229, 1);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                {
                    tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(229, 1), -1);
                    return true;
                }
            }
            else if (client.getWidget(233, 3) != null) {
                Widget w = client.getWidget(233, 3);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                {
                    tClient.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(233, 3), -1);
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * check if a chat dialogue is present
     * @return true if present
     */
    public static boolean dialoguePresent() {
        Client client = Static.RL_CLIENT;
        return Static.invoke(() -> {
            if (client.getWidget(WidgetInfo.DIALOG_NPC_CONTINUE) != null) {
                return true;
            }
            else if (client.getWidget(633, 0) != null) {
                return true;
            }
            else if (client.getWidget(217, 5) != null) {
                return true;
            }
            else if (client.getWidget(WidgetInfo.DIALOG_SPRITE) != null) {
                return true;
            }
            else if (client.getWidget(11, 0) != null) {
                return true;
            }
            else if (client.getWidget(229, 2) != null) {
                Widget w = client.getWidget(229, 2);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                    return true;
            }
            else if (client.getWidget(229, 1) != null) {
                Widget w = client.getWidget(229, 1);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                    return true;
            }
            else if(client.getWidget(233, 3) != null) {
                Widget w = client.getWidget(233, 3);
                if(w != null && w.getText() != null && w.getText().equals("Click here to continue"))
                    return true;
            }

            return client.getWidget(219, 1) != null || getWidget(ComponentID.DIALOG_OPTION_OPTIONS) != null;
        });
    }

    public static void selectOption(Object option)
    {
        if(option instanceof Integer)
            selectOption((int)option);
        else if(option instanceof String)
            selectOption((String)option);
    }

    /**
     * select a dialogue option
     * @param option option
     */
    public static void selectOption(int option) {
        Static.invoke(() -> Static.T_CLIENT.getPacketWriter().resumePauseWidgetPacket(WidgetInfo.PACK(219, 1), option));
    }

    public static void resumePause(int widgetID, int optionIndex) {
        Static.invoke(() -> Static.T_CLIENT.getPacketWriter().resumePauseWidgetPacket(widgetID, optionIndex));
    }

    /**
     * select a dialogue option
     * @param option option
     */
    public static boolean selectOption(String option) {
        Client client = Static.RL_CLIENT;
        return Static.invoke(() -> {
            Widget widget = client.getWidget(219, 1);
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

    public static List<String> getOptions() {
        Client client = Static.RL_CLIENT;
        return Static.invoke(() -> {
            List<String> options = new ArrayList<>();
            Widget widget = client.getWidget(219, 1);
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

    /**
     * select multiple sequential options
     * @param options options
     * @param delay delay a tick between each
     */
    public static void selectOption(int[] options, boolean delay) {
        int i = 0;
        for(int o : options)
        {
            i++;
            selectOption(o);
            if(i % 10 == 0 || delay)
            {
                Delays.tick(1);
            }
        }
    }

    public static boolean continueSingle()
    {
        return continueDialogue();
    }

    public static void makeX(int quantity)
    {
        TClient client = Static.T_CLIENT;
        Static.invoke(() -> {
            client.getPacketWriter().resumePauseWidgetPacket(17694734, quantity);
        });
    }


    public static boolean optionPresent(String option)
    {
        Client client = Static.RL_CLIENT;
        return Static.invoke(() -> {
            Widget widget = client.getWidget(219, 1);
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

    public static Widget getWidget(int id)
    {
        Client client = Static.RL_CLIENT;
        int group = WidgetInfo.TO_GROUP(id);
        int child = WidgetInfo.TO_CHILD(id);
        return client.getWidget(group, child);
    }

    public static void resumeNumericDialogue(int value)
    {
        Static.invoke(() -> {
            Static.T_CLIENT.getPacketWriter().resumeCountDialoguePacket(value);
            ClientScript.closeNumericInputDialogue();
        });
    }
}