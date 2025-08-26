package com.tonic.rlmixins;

import com.tonic.injector.annotations.MethodOverride;
import com.tonic.injector.annotations.Mixin;

@Mixin("net/runelite/client/Updater")
public class UpdaterMixin {
    @MethodOverride("tryUpdate")
    public void tryUpdate() {
        return;
    }
}
