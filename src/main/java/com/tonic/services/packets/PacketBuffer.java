package com.tonic.services.packets;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PacketBuffer {
    private final int packetId;
    @Setter
    private ByteBuf payload;

    @Setter
    private int offset;

    @Setter
    private int trueLength = 0;

    public PacketBuffer(int packetId, byte[] payload) {
        this.packetId = packetId;
        this.payload = ByteBufferPool.allocate(payload.length);
        this.payload.writeBytes(payload);
        this.offset = 0;
    }

    public void dispose()
    {
        ByteBufferPool.release(payload);
    }

    public byte readByte() {
        return payload.getByte(offset++);
    }

    public byte readByteAdd()
    {
        return (byte)(readByte() - 128);
    }

    public byte readByteNeg()
    {
        return (byte)(-readByte());
    }

    public byte readByteSub()
    {
        return (byte)(readByte() + 128);
    }

    public int readLengthByte() {
        int var = payload.getByte(offset - 1);
        if (var >= 0) {
            return var;
        }
        return -1;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public boolean readBooleanAdd() {
        return readByteAdd() != 0;
    }

    public boolean readBooleanNeg() {
        return readByteNeg() != 0;
    }

    public boolean readBooleanSub() {
        return readByteSub() != 0;
    }

    public int readUnsignedShort() {
        int value = ((payload.getByte(offset) & 0xFF) << 8) | (payload.getByte(offset + 1) & 0xFF);
        offset += 2;
        return value;
    }

    public int readUnsignedShortAdd() {
        int low = payload.getByte(offset++) & 0xFF;
        if (low < 128) {
            low += 128;
        } else {
            low -= 128;
        }
        int high = (payload.getByte(offset++) & 0xFF) << 8;
        return high | low;
    }

    public int readUnsignedShortLE() {
        int value = ((payload.getByte(offset + 1) & 0xFF) << 8) | (payload.getByte(offset) & 0xFF);
        offset += 2;
        return value;
    }

    public int readUnsignedShortAddLE() {
        int value = ((payload.getByte(offset) & 0xFF) << 8) | ((payload.getByte(offset + 1) & 0xFF) - 128 & 0xFF);
        offset += 2;
        return value;
    }

    public int readLengthShort() {
        if (offset < 2) {
            return -1;
        }
        int length = ((payload.getByte(offset - 2) & 0xFF) << 8) | (payload.getByte(offset - 1) & 0xFF);
        offset -= length + 2;
        return length;
    }

    public int readMedium() {
        offset += 3;
        int b1 = payload.getByte(offset - 3) & 0xFF;
        int b2 = payload.getByte(offset - 2) & 0xFF;
        int b3 = payload.getByte(offset - 1) & 0xFF;
        return (b1 << 16) | (b2 << 8) | b3;
    }

    public int readInt() {
        return ((payload.getByte(offset++) & 0xFF) << 24) |
                ((payload.getByte(offset++) & 0xFF) << 16) |
                ((payload.getByte(offset++) & 0xFF) << 8) |
                (payload.getByte(offset++) & 0xFF);
    }

    public int readIntME() {
        return ((payload.getByte(offset++) & 0xFF) << 8) |
                ((payload.getByte(offset++) & 0xFF)) |
                ((payload.getByte(offset++) & 0xFF) << 24) |
                ((payload.getByte(offset++) & 0xFF) << 16);
    }

    public int readIntLE() {
        return (payload.getByte(offset++) & 0xFF) |
                ((payload.getByte(offset++) & 0xFF) << 8) |
                ((payload.getByte(offset++) & 0xFF) << 16) |
                ((payload.getByte(offset++) & 0xFF) << 24);
    }

    public int readIntIME() {
        return ((payload.getByte(offset++) & 0xFF) << 16) |
                ((payload.getByte(offset++) & 0xFF) << 24) |
                (payload.getByte(offset++) & 0xFF) |
                ((payload.getByte(offset++) & 0xFF) << 8);
    }

    public int readVarInt() {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public int readLengthInt() {
        if (offset < 4) {
            return -1;
        }
        int length = ((payload.getByte(offset - 4) & 0xFF) << 24) | ((payload.getByte(offset - 3) & 0xFF) << 16) |
                ((payload.getByte(offset - 2) & 0xFF) << 8) | (payload.getByte(offset - 1) & 0xFF);
        offset -= length + 4;
        return length;
    }

    public long readLong() {
        long var1 = (long)readInt() & 4294967295L;
        long var2 = (long)readInt() & 4294967295L;
        return var2 + (var1 << 32);
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }
}