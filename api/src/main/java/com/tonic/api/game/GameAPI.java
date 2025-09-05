package com.tonic.api.game;

import com.tonic.Static;
import com.tonic.api.widgets.WidgetAPI;
import com.tonic.types.WidgetInfoExtended;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

public class GameAPI
{
    public static void logout()
    {
        Widget logoutButton = WidgetAPI.get(WidgetInfo.LOGOUT_BUTTON);
        if (logoutButton != null)
        {
            WidgetAPI.interact(1, ComponentID.LOGOUT_PANEL_LOGOUT_BUTTON, -1, -1);
        }

        logoutButton = WidgetAPI.get(WidgetInfoExtended.WORLD_SWITCHER_LOGOUT_BUTTON);
        if (logoutButton != null)
        {
            WidgetAPI.interact(1, WidgetInfoExtended.WORLD_SWITCHER_LOGOUT_BUTTON, -1, -1);
        }
    }

    public static int getWildyLevel()
    {
        Widget wildyLevelWidget = WidgetAPI.get(WidgetInfo.PVP_WILDERNESS_LEVEL);
        if (!WidgetAPI.isVisible(wildyLevelWidget))
        {
            return 0;
        }

        // Dmm
        if (wildyLevelWidget.getText().contains("Guarded")
                || wildyLevelWidget.getText().contains("Protection"))
        {
            return 0;
        }

        if (wildyLevelWidget.getText().contains("Deadman"))
        {
            return Integer.MAX_VALUE;
        }
        String widgetText = wildyLevelWidget.getText();
        if (widgetText.isEmpty())
        {
            return 0;
        }
        if (widgetText.equals("Level: --"))
        {
            Client client = Static.getClient();
            Player local = client.getLocalPlayer();
            WorldView worldView = client.getTopLevelWorldView();
            int y = WorldPoint.fromLocal(worldView,
                    local.getLocalLocation().getX(),
                    local.getLocalLocation().getY(),
                    worldView.getPlane()).getY();
            return 2 + (y - 3528) / 8;
        }
        String levelText = widgetText.contains("<br>") ? widgetText.substring(0, widgetText.indexOf("<br>")) : widgetText;
        return Integer.parseInt(levelText.replace("Level: ", ""));
    }

    public static boolean isLoggedIn()
    {
        Client client = Static.getClient();
        return client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING;
    }

    public static int getRealSkillLevel(Skill skill)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getRealSkillLevel(skill));
    }

    public static int getBoostedSkillLevel(Skill skill)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getBoostedSkillLevel(skill));
    }

    public static int getSkillExperience(Skill skill)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getSkillExperience(skill));
    }
}
