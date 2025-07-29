package com.tonic.mixins;

import com.tonic.api.TClientPacket;
import com.tonic.api.TPacketBuffer;
import com.tonic.api.TPacketBufferNode;
import com.tonic.api.TPacketWriter;
import com.tonic.injector.Mappings;
import com.tonic.injector.annotations.Inject;
import com.tonic.injector.annotations.MethodHook;
import com.tonic.injector.annotations.Mixin;
import com.tonic.services.packets.PacketBuffer;
import com.tonic.services.packets.PacketMapReader;

import java.util.Arrays;

@Mixin(Mappings.packetWriterClassName)
public class TPacketWriterMixin implements TPacketWriter
{
    @MethodHook(name = Mappings.addNodeMethodName, desc = "(L" + Mappings.packetBufferNodeClassName + ";B)V")
    public static void onAddNode(TPacketBufferNode node)
    {
        if(node == null ||  node.getClientPacket() == null)
            return;

        PacketBuffer pb = getPB(node);
        String out;

        try
        {
            out = PacketMapReader.prettify(pb);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            out = "[oops]";
        }
        if (!out.startsWith("[UNKNOWN(")) {
            System.out.println(out);
        }
        pb.dispose();
    }

    @Inject
    private static PacketBuffer getPB(TPacketBufferNode node) {
        TPacketBuffer buffer = node.getPacketBuffer();
        TClientPacket packet = node.getClientPacket();

        int offset = buffer.getOffset() * Integer.parseInt(Mappings.bufferOffsetMultiplier);
        int id = packet.getId() * Integer.parseInt(Mappings.clientPacketIdMultiplier);
        int len = packet.getLength() * Integer.parseInt(Mappings.clientPacketLengthMultiplier);

        byte[] bytes = buffer.getArray();
        byte[] payload = Arrays.copyOfRange(
                bytes, 1, (len > 0) ? (1 + len) : offset
        );

        return new PacketBuffer(id, payload);
    }
}
