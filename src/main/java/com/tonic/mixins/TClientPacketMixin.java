package com.tonic.mixins;

import com.tonic.api.TClientPacket;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import lombok.Getter;

@Mixin(Mappings.clientPacketClassName)
@Getter
public class TClientPacketMixin implements TClientPacket {
    @Shadow(name = Mappings.clientPacketIdField, desc = "I")
    public int id;

    @Shadow(name = Mappings.clientPacketLengthField, desc = "I")
    public int length;
}
