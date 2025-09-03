package com.tonic.automation.widgets;

import com.tonic.types.EmoteID;

public class EmoteAPI
{
    public static void perform(int emoteId) {
        WidgetAPI.interact(1, EmoteID.EMOTES_WIDGET_ID, emoteId);
    }

    public static void perform(EmoteID emoteId) {
        WidgetAPI.interact(1, EmoteID.EMOTES_WIDGET_ID, emoteId.getId());
    }
}
