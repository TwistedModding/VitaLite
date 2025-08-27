package com.tonic.mixins;

import com.tonic.api.TMouseHandler;
import com.tonic.injector.annotations.Inject;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;

@Mixin("MouseHandler")
public class TMouseHandlerMixin implements TMouseHandler
{
    @Shadow("MouseHandler_lastPressedTimeMillis")
    private static long mouseLastPressedMillis;

    @Inject
    public long getMouseLastPressedMillis() {
        return mouseLastPressedMillis;
    }

    @Inject
    public void setMouseLastPressedMillis(long millis) {
        mouseLastPressedMillis = millis;
    }
}
