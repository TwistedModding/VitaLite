package com.tonic.mixins;

import com.tonic.api.TPacketBuffer;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Mixin;

@Mixin(Mappings.packetBufferClassName)
public abstract class TPacketBufferMixin implements TPacketBuffer
{

}
