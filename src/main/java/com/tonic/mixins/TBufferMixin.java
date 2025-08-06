package com.tonic.mixins;

import com.tonic.api.TBuffer;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import lombok.Getter;

@Mixin("Buffer")
@Getter
public class TBufferMixin implements TBuffer
{
    @Shadow("array")
    public byte[] array;

    @Shadow("offset")
    public int offset;
}
