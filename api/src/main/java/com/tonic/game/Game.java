package com.tonic.game;

import com.tonic.Static;
import net.runelite.api.GameState;

public class Game {
    public static int getVar(int varbit)
    {
        return Static.invoke(() -> Static.T_CLIENT.getVarbitValue(Static.RL_CLIENT.getVarps(), varbit));
    }

    public static int getVarp(int varp)
    {
        return Static.invoke(() -> Static.T_CLIENT.getVarpValue(varp));
    }

    public static void setVar(int varbit, int value)
    {
        Static.invoke(() -> Static.T_CLIENT.setVarbit(varbit, value));
    }

    public static boolean isLoggedIn()
    {
        return Static.RL_CLIENT.getGameState() == GameState.LOGGED_IN || Static.RL_CLIENT.getGameState() == GameState.LOADING;
    }
}
