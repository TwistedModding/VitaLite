package com.tonic.mixins;

import com.tonic.api.TBuffer;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import lombok.Getter;

@Mixin(Mappings.bufferClassName)
@Getter
public class TBufferMixin implements TBuffer
{
    @Shadow(name = Mappings.bufferArrayFieldName, desc = "[B")
    public byte[] array;

    @Shadow(name = Mappings.bufferOffsetField, desc = "I")
    public int offset;
}
