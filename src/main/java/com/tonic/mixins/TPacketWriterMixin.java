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
import net.runelite.api.Client;
import net.runelite.api.GameState;

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

    @Override
    @Inject
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

    @Shadow("resumePauseWidget")
    @Override
    public abstract void resumePauseWidget(int widgetID, int optionIndex);

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

    @Inject
    @Override
    public void click(int mouseButton, int mouseX, int mouseY)
    {
        int mouseInfo = (32767 << 1) + (mouseButton);
        MapEntry entry = PacketMapReader.get("OP_MOUSE_CLICK");
        Map<String,Object> args = new HashMap<>();
        args.put("mouseInfo", mouseInfo);
        args.put("x", mouseX);
        args.put("y", mouseY);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

//    @Inject
//    @Override
//    public void sendChatPacket(int type, String text) {
//        if (text.length() >= 80)
//            text = text.substring(0, 79);
//        TPacketBufferNode packetBufferNode = sendChat(type, text, null, -1);
//        if(packetBufferNode != null)
//        {
//            this.addNode(packetBufferNode);
//        }
//    }

//    @Shadow("method10225")
//    public abstract TPacketBufferNode sendChat(int type, String text, TLanguage language, int unknown);

    @Inject
    @Override
    public void sendWidgetTargetOnGameObjectPacket(int selectedWidgetId, int itemId, int slot, int identifier, int worldX, int worldY, boolean ctrl)
    {
        MapEntry entry = PacketMapReader.get("OP_WIDGET_TARGET_ON_GAME_OBJECT");
        Map<String,Object> args = new HashMap<>();
        args.put("selectedWidgetId", selectedWidgetId);
        args.put("itemId", itemId);
        args.put("slot", slot);
        args.put("identifier", identifier);
        args.put("worldX", worldX);
        args.put("worldY", worldY);
        args.put("ctrl", ctrl ? 0 : 1);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Inject
    @Override
    public void sendWidgetTargetOnNpcPacket(int identifier, int selectedWidgetId, int itemId, int slot, boolean ctrl)
    {
        MapEntry entry = PacketMapReader.get("OP_WIDGET_TARGET_ON_NPC");
        Map<String,Object> args = new HashMap<>();
        args.put("selectedWidgetId", selectedWidgetId);
        args.put("itemId", itemId);
        args.put("slot", slot);
        args.put("identifier", identifier);
        args.put("ctrl", ctrl ? 0 : 1);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Inject
    @Override
    public void sendWidgetTargetOnPlayerPacket(int identifier, int selectedWidgetId, int itemId, int slot, boolean ctrl)
    {
        MapEntry entry = PacketMapReader.get("OP_WIDGET_TARGET_ON_PLAYER");
        Map<String,Object> args = new HashMap<>();
        args.put("selectedWidgetId", selectedWidgetId);
        args.put("itemId", itemId);
        args.put("slot", slot);
        args.put("identifier", identifier);
        args.put("ctrl", ctrl ? 0 : 1);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Inject
    @Override
    public void resumeStringDialogue(String text)
    {
//        RSClientPacket packet = client.newClientPacket(3, -1);
//        RSPacketBufferNode packetBufferNode = client.getPacketBufferNode(packet, getIsaacCipher());
//        if(packetBufferNode != null)
//        {
//            packetBufferNode.getPacketBuffer().writeByte(text.length() + 1);
//            packetBufferNode.getPacketBuffer().writeStringCp1252NullTerminated(text);
//            addNode(packetBufferNode);
//        }

        MapEntry entry = PacketMapReader.get("OP_RESUME_STRINGDIALOG");
        Map<String,Object> args = new HashMap<>();
        args.put("length", text.length());
        args.put("var7", text);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Inject
    @Override
    public void resumeNameDialogue(String text)
    {
//        RSClientPacket packet = client.newClientPacket(31, -1);
//        RSPacketBufferNode packetBufferNode = client.getPacketBufferNode(packet, getIsaacCipher());
//        if(packetBufferNode != null)
//        {
//            packetBufferNode.getPacketBuffer().writeByte(text.length() + 1);
//            packetBufferNode.getPacketBuffer().writeStringCp1252NullTerminated(text);
//            addNode(packetBufferNode);
//        }
        MapEntry entry = PacketMapReader.get("OP_RESUME_NAMEDIALOG");
        Map<String,Object> args = new HashMap<>();
        args.put("length", text.length());
        args.put("var7", text);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Override
    public void sendObjectActionPacket(int type, int identifier, int worldX, int worldY, boolean ctrl)
    {
        StringBuilder name = new StringBuilder("OP_GAME_OBJECT_ACTION_");
        name.append(type);
        MapEntry entry = PacketMapReader.get(name.toString());
        if(entry == null)
        {
            System.err.println("Packets::sendObjectActionPacket invalid type");
            return;
        }

        Map<String,Object> args = new HashMap<>();
        args.put("identifier", identifier);
        args.put("ctrl", ctrl ? 1 : 0);
        args.put("worldX", worldX);
        args.put("worldY", worldY);

        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Override
    public void sendWidgetOnWidgetPacket(int selectedWidgetId, int itemId, int slot, int targetWidgetId, int itemId2, int slot2)
    {
        MapEntry entry = PacketMapReader.get("OP_WIDGET_TARGET_ON_WIDGET");
        Map<String,Object> args = new HashMap<>();
        args.put("selectedWidgetId", selectedWidgetId);
        args.put("itemId", itemId);
        args.put("slot", slot);
        args.put("targetWidgetID", targetWidgetId);
        args.put("identifier2", itemId2);
        args.put("param0", slot2);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Override
    public void sendWidgetOnGroundItemPacket(int selectedWidgetId, int itemId, int slot, int groundItemID, int worldX, int worldY, boolean ctrl)
    {
        MapEntry entry = PacketMapReader.get("OP_WIDGET_TARGET_ON_GROUND_ITEM");
        Map<String,Object> args = new HashMap<>();
        args.put("selectedWidgetId", selectedWidgetId);
        args.put("itemId", itemId);
        args.put("slot", slot);
        args.put("identifier", groundItemID);
        args.put("worldX", worldX);
        args.put("worldY", worldY);
        args.put("ctrl", ctrl ? 0 : 1);
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }

    @Override
    public void sendInterfaceClosePacket()
    {
        MapEntry entry = PacketMapReader.get("OP_INTERFACE_CLOSE");
        Map<String,Object> args = new HashMap<>();
        this.addNode(PacketMapReader.createBuffer(entry, args).toPacketBufferNode(client));
    }
}
