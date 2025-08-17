package com.tonic.events;

import com.tonic.api.TPacketBufferNode;
import com.tonic.packets.PacketBuffer;
import com.tonic.packets.PacketMapReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PacketSent {
    private final int id;
    private final byte[] payload;

    @Override
    public String toString()
    {
        PacketBuffer pb = new PacketBuffer(id, payload);
        String out = PacketMapReader.prettify(pb);
        pb.dispose();
        return out;
    }
}
