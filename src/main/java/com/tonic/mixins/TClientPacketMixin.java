package com.tonic.mixins;

import com.tonic.api.TClientPacket;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import lombok.Getter;

@Mixin("ClientPacket")
@Getter
public class TClientPacketMixin implements TClientPacket {
    @Shadow("length")
    public int id;

    @Shadow("id")
    public int length;
}
