package com.tonic.services.packets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;

public class ByteBufferPool
{
    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    public static ByteBuf allocate(int size) {
        return ALLOCATOR.buffer(size);
    }

    public static void release(ByteBuf buffer) {
        if (buffer != null) {
            buffer.release();
        }
    }
}