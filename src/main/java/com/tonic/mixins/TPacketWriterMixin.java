package com.tonic.mixins;

import com.tonic.api.*;
import com.tonic.injector.annotations.Inject;
import com.tonic.injector.annotations.MethodHook;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.annotations.Shadow;
import com.tonic.services.packets.PacketBuffer;
import com.tonic.services.packets.PacketMapReader;
import com.tonic.services.packets.types.MapEntry;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Getter
@Mixin("PacketWriter")
public abstract class TPacketWriterMixin implements TPacketWriter
{
    @Shadow("isaacCipher")
    public TIsaacCipher isaacCipher;

    @Shadow("client")
    public static TClient client;

    @Shadow("addNode")
    public abstract void addNode(TPacketBufferNode node);

    @MethodHook("addNode")
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

    @MethodHook("addNode2")
    public static void onAddNode2(TPacketWriter packetWriter, TPacketBufferNode node)
    {
        if(packetWriter == null || node == null ||  node.getClientPacket() == null)
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

        int offset = buffer.getOffset();
        int id = packet.getId();
        int len = packet.getLength();

        byte[] bytes = buffer.getArray();
        byte[] payload = Arrays.copyOfRange(
                bytes, 1, (len > 0) ? (1 + len) : offset
        );

        return new PacketBuffer(id, payload);
    }

    @Inject
    @Override
    public void widgetAction(int type, int widgetId, int childId, int itemId)
    {
        MapEntry entry = PacketMapReader.get("OP_WIDGET_ACTION");
        Map<String,Object> args = new HashMap<>();
        args.put("widgetId", widgetId);
        args.put("childId", childId);
        args.put("itemId", itemId);
        args.put("type", type);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Inject
    @Override
    public void resumeCountDialogue(int count)
    {
        MapEntry entry = PacketMapReader.get("OP_RESUME_COUNTDIALOG");
        Map<String,Object> args = new HashMap<>();
        args.put("count", count);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

//    @Shadow("resumePauseWidget")
//    @Override
//    public abstract void resumePauseWidget(int widgetID, int optionIndex);

    @Inject
    @Override
    public void resumeObjectDialogue(int id) {
        MapEntry entry = PacketMapReader.get("OP_RESUME_OBJDIALOG");
        Map<String,Object> args = new HashMap<>();
        args.put("id", id);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Inject
    @Override
    public void doWalk(int worldX, int worldY, boolean ctrl)
    {
        MapEntry entry = PacketMapReader.get("OP_WALK");
        Map<String,Object> args = new HashMap<>();
        args.put("worldX", worldX);
        args.put("worldY", worldY);
        args.put("ctrl", ctrl ? 1 : 0);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }
}
