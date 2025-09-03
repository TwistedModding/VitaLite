package com.tonic.packets.types;

import com.google.gson.annotations.Expose;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MapEntry
{
    @Expose
    private String name;
    @Expose
    private PacketEntry packet;
    @Expose
    private List<String> writes;
    @Expose
    private List<String> reads;
    @Expose
    private List<String> obfuWrites;
    @Expose
    private List<String> args;

    @Override
    public String toString()
    {
        StringBuilder out = new StringBuilder(name + " [" + packet.getId() + "]\n");
        out.append("\tPacketNode packetNode = new PacketNode(ClientPacket.").append(packet.getField()).append(", isaacShit);\n");
        for(int i = 0; i < writes.size(); i++)
        {
            out.append("\tpacketNode.").append(writes.get(i)).append("(").append(args.get(i)).append(");\n");
        }
        out.append("\tpacketWriter.addNode(packetNode);\n");
        return out.toString();
    }

    public String toWriter()
    {
        StringBuilder source = new StringBuilder("public void send" + transformString(name) + "(");
        for(String arg : sortedArgs())
        {
            if(arg.equals("ctrl"))
            {
                source.append("boolean ").append(arg).append(", ");
                continue;
            }
            source.append("int ").append(arg).append(", ");
        }
        if(source.toString().endsWith(", "))
        {
            source = new StringBuilder(source.substring(0, source.length() - 2));
        }
        source.append(")\n{\n");
        source.append("\tRSClientPacket packet = getRSClient().newClientPacket(").append(packet.getId()).append(", ").append(packet.getLength()).append(");\n");
        source.append("\tRSPacketBufferNode packetBufferNode = getRSClient().getPacketBufferNode(packet, getIsaacCipher());\n");
        source.append("\tif(packetBufferNode == null)\n\t\treturn;\n");
        for(int i = 0; i < writes.size(); i++)
        {
            if(args.get(i).equals("ctrl"))
            {
                source.append("\tpacketBufferNode.getPacketBuffer().").append(writes.get(i)).append("(").append(args.get(i)).append(" ? 1 : 0);\n");
                continue;
            }
            source.append("\tpacketBufferNode.getPacketBuffer().").append(writes.get(i)).append("(").append(args.get(i)).append(");\n");
        }
        source.append("\taddNode(packetBufferNode);\n");
        source.append("}\n");
        return source.toString();
    }

    private static String transformString(String input) {
        input = input.replace("OP_", "");
        String[] words = input.split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : words) {
            String word = s.toLowerCase();
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.toString();
    }
    private List<String> sorted = new ArrayList<>()
    {{
        add("selectedWidgetId");
        add("itemId");
        add("slot");
        add("id");
        add("identifier");
        add("worldX");
        add("worldY");
        add("ctrl");
        add("targetWidgetID");
        add("identifier2");
        add("param0");
    }};

    public List<String> sortedArgs()
    {
        List<String> old = new ArrayList<>(args);
        List<String> out = new ArrayList<>();
        for(String arg : sorted)
        {
            if(!old.contains(arg))
                continue;
            out.add(arg);
            old.remove(arg);
        }
        if(!old.isEmpty())
        {
            out.addAll(old);
        }
        return out;
    }
}