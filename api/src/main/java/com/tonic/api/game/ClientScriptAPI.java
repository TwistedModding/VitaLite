package com.tonic.api.game;

import com.tonic.Static;
import com.tonic.types.Tab;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

public class ClientScriptAPI
{
    public static void closeNumericInputDialogue()
    {
        Client client = Static.getClient();
        Static.invoke(() -> {
            Widget w = client.getWidget(WidgetInfo.CHATBOX_INPUT);
            Widget w2 = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
            if(w != null || w2 != null)
            {
                client.runScript(138);
            }
        });
    }

    public static void switchTabs(int tab)
    {
        Client client = Static.getClient();
        Static.invoke(() -> client.runScript(915, tab));
    }

    public static void switchTabs(Tab tab)
    {
        Client client = Static.getClient();
        Static.invoke(() -> client.runScript(915, tab.getTabVarbit()));
    }

    public static void runScript(int id, Object... args)
    {
        Client client = Static.getClient();
        Static.invoke(() -> client.runScript(id, args));
    }
}
