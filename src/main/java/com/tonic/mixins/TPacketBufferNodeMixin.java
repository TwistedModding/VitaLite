package com.tonic.mixins;

import com.tonic.api.TClientPacket;
import com.tonic.api.TPacketBuffer;
import com.tonic.api.TPacketBufferNode;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import lombok.Getter;

@Mixin(Mappings.packetBufferNodeClassName)
@Getter
public class TPacketBufferNodeMixin implements TPacketBufferNode
{
    @Shadow(name = Mappings.packetBufferFieldName, desc = "L" + Mappings.packetBufferClassName + ";")
    private TPacketBuffer packetBuffer;

    @Shadow(name = Mappings.clientPacketFieldName, desc = "L" + Mappings.clientPacketClassName + ";")
    private TClientPacket clientPacket;
}
