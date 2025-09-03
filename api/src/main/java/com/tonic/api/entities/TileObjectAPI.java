package com.tonic.api.entities;

import com.tonic.Static;
import com.tonic.api.TClient;
import com.tonic.types.TileObjectEx;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;

public class TileObjectAPI
{
    /**
     * interact with a tile object
     * @param object object
     * @param action action
     */
    public static void interact(TileObjectEx object, int action)
    {
        Client client = Static.getClient();
        TClient tclient = Static.getClient();
        if(!client.getGameState().equals(GameState.LOGGED_IN) || object == null)
            return;

        Static.invoke(() ->
        {
            tclient.getPacketWriter().clickPacket(0, -1, -1);
            tclient.getPacketWriter().objectActionPacket(action, object.getId(), object.getWorldLocation().getX(), object.getWorldLocation().getY(), false);
        });
    }

    /**
     * interact with a tile object
     * @param object object
     * @param action action
     */
    public static void interact(TileObject object, int action)
    {
        Client client = Static.getClient();
        TClient tclient = Static.getClient();
        if(!client.getGameState().equals(GameState.LOGGED_IN) || object == null)
            return;

        Static.invoke(() ->
        {
            tclient.getPacketWriter().clickPacket(0, -1, -1);
            tclient.getPacketWriter().objectActionPacket(action, object.getId(), object.getWorldLocation().getX(), object.getWorldLocation().getY(), false);
        });
    }

    /**
     * interact with a tile object
     * @param object object
     * @param action action
     */
    public static void interact(TileObjectEx object, String action)
    {
        Client client = Static.getClient();
        TClient tclient = Static.getClient();
        if(!client.getGameState().equals(GameState.LOGGED_IN) || object == null)
            return;

        int actionIndex = object.getActionIndex(action);

        Static.invoke(() ->
        {
            tclient.getPacketWriter().clickPacket(0, -1, -1);
            tclient.getPacketWriter().objectActionPacket(actionIndex, object.getId(), object.getWorldLocation().getX(), object.getWorldLocation().getY(), false);
        });
    }

    /**
     * interact with a tile object
     * @param object object
     * @param action action
     */
    public static void interact(TileObject object, String action)
    {
        Client client = Static.getClient();
        TClient tclient = Static.getClient();
        if(!client.getGameState().equals(GameState.LOGGED_IN) || object == null)
            return;

        int actionIndex = getAction(object, action);

        Static.invoke(() ->
        {
            tclient.getPacketWriter().clickPacket(0, -1, -1);
            tclient.getPacketWriter().objectActionPacket(actionIndex, object.getId(), object.getWorldLocation().getX(), object.getWorldLocation().getY(), false);
        });
    }

    private static int getAction(TileObject object, String action) {
        String[] actions = getActions(object);
        for(int i = 0; i < actions.length; i++)
        {
            if(actions[i] == null)
                continue;
            if(!actions[i].toLowerCase().contains(action.toLowerCase()))
                continue;
            return i;
        }
        return -1;
    }

    private static String[] getActions(TileObject tileObject) {
        Client client = Static.getClient();
        return Static.invoke(() -> {
            ObjectComposition composition = client.getObjectDefinition(tileObject.getId());
            if(composition == null)
                return new String[]{};
            return composition.getActions();
        });
    }
}
