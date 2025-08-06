package com.tonic.mixins;

import com.tonic.api.TClientPacket;
import com.tonic.api.TPacketBuffer;
import com.tonic.api.TPacketBufferNode;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import lombok.Getter;

@Mixin("PacketBufferNode")
@Getter
public class TPacketBufferNodeMixin implements TPacketBufferNode
{
    @Shadow("packetBuffer")
    private TPacketBuffer packetBuffer;

    @Shadow("clientPacket")
    private TClientPacket clientPacket;
}
