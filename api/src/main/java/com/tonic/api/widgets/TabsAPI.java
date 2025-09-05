package com.tonic.api.widgets;

import com.tonic.Static;
import com.tonic.api.game.ClientScriptAPI;
import com.tonic.types.Tab;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientInt;

import java.util.Arrays;

public class TabsAPI
{
    public static void open(Tab tab)
    {
        Client client = Static.getClient();
        if (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOADING)
        {
            return;
        }

        ClientScriptAPI.switchTabs(tab);
    }

    public static boolean isOpen(Tab tab)
    {
        Client client = Static.getClient();
        return client.getVarcIntValue(VarClientInt.INVENTORY_TAB) == Arrays.asList(Tab.values()).indexOf(tab);
    }
}
