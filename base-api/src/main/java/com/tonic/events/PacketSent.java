package com.tonic.events;

import com.tonic.api.TPacketBufferNode;
import com.tonic.packets.PacketBuffer;
import com.tonic.packets.PacketMapReader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class PacketSent {
    private final int id;
    private final int length;
    private final byte[] payload;
    private PacketBuffer buffer;

    public PacketBuffer getBuffer()
    {
        if(buffer == null)
        {
            buffer = new PacketBuffer(id, payload);
        }
        return buffer;
    }

    @Override
    public String toString()
    {
        PacketBuffer pb = getBuffer();
        String out = PacketMapReader.prettify(pb);
        pb.dispose();
        return out;
    }
}
