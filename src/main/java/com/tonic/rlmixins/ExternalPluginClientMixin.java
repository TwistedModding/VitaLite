package com.tonic.rlmixins;

import com.tonic.injector.annotations.MethodOverride;
import com.tonic.injector.annotations.Mixin;

@Mixin("net/runelite/client/externalplugins/ExternalPluginClient")
public class ExternalPluginClientMixin {

    @MethodOverride("submitPlugins")
    public void submitPlugins() {
        return;
    }
}
