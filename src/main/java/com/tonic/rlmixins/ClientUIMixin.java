package com.tonic.rlmixins;

import com.tonic.injector.annotations.At;
import com.tonic.injector.annotations.AtTarget;
import com.tonic.injector.annotations.Insert;
import com.tonic.injector.annotations.Mixin;
import com.tonic.runelite.ClientUIUpdater;

@Mixin("net/runelite/client/ui/ClientUI")
public class ClientUIMixin {
    @Insert(
            method = "lambda$init$6",
            at = @At(value = AtTarget.INVOKE, owner = "net/runelite/client/ui/ClientUI", target = "updateFrameConfig"),
            ordinal = -1
    )
    public static void initVita() {
        ClientUIUpdater.inject();
    }
}
