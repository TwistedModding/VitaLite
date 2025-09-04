package com.tonic.api.game;

import com.tonic.Static;
import net.runelite.api.Client;

public class VarAPI
{
    public static int getVar(int varbit)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getVarbitValue(varbit));
    }

    public static int getVarp(int varp)
    {
        Client client = Static.getClient();
        return Static.invoke(() -> client.getVarpValue(varp));
    }

    public static void setVar(int varbit, int value)
    {
        Client client = Static.getClient();
        Static.invoke(() -> client.setVarbit(varbit, value));
    }
}
