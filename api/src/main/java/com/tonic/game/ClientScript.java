package com.tonic.game;

import com.tonic.Static;
import com.tonic.widgets.Dialogue;
import net.runelite.api.widgets.Widget;

public class ClientScript
{
    public static void closeNumericInputDialogue()
    {
        Static.invoke(() -> {
            Widget w = Dialogue.getWidget(10616886);
            Widget w2 = Dialogue.getWidget(10616875);
            if(w != null || w2 != null)
            {
                Static.RL_CLIENT.runScript(138);
            }
        });
    }
}
