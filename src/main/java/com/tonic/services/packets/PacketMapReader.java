package com.tonic.services.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tonic.services.packets.types.MapEntry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PacketMapReader
{
    private static List<MapEntry> defs;
    private static final Gson gson = new GsonBuilder().create();

    public static List<MapEntry> get()
    {
        if(defs == null)
        {
            fillMaps();
        }
        return defs;
    }

    public static MapEntry get(String packet)
    {
        if(defs == null)
        {
            fillMaps();
        }
        return defs.stream()
                .filter(e -> e.getName().equals(packet))
                .findFirst().orElse(null);
    }

    public static String prettify(PacketBuffer buffer)
    {
        MapEntry entry = get().stream()
                .filter(e -> e.getPacket().getId() == buffer.getPacketId())
                .findFirst().orElse(null);
        if(entry == null)
        {
            return "[UNKNOWN(" + buffer.getPacketId() + ")] " + buffer;
        }

        StringBuilder out = new StringBuilder("[" + entry.getName() + "(" + entry.getPacket().getId() + ")] ");
        long num;
        for(int i = 0; i < entry.getReads().size(); i++)
        {
            if(isParsableAsNumber(entry.getArgs().get(i)))
            {
                doRead(buffer, entry.getReads().get(i));
            }
            else
            {
                num = doRead(buffer, entry.getReads().get(i));
                out.append(entry.getArgs().get(i)).append("=").append(num).append(", ");
            }
        }
        buffer.setOffset(0);
        return out.toString();
    }

    private static long doRead(PacketBuffer buffer, String method)
    {
        switch (method) {
            case "readByte":
                return buffer.readByte();
            case "readByteAdd":
                return buffer.readByteAdd();
            case "readByteNeg":
                return buffer.readByteNeg();
            case "readByteSub":
                return buffer.readByteSub();
            case "readLengthByte":
                return buffer.readLengthByte();
            case "readBoolean":
                return buffer.readBoolean() ? 1 : 0;
            case "readBooleanAdd":
                return buffer.readBooleanAdd() ? 1 : 0;
            case "readBooleanNeg":
                return buffer.readBooleanNeg() ? 1 : 0;
            case "readBooleanSub":
                return buffer.readBooleanSub() ? 1 : 0;
            case "readShort":
                return buffer.readUnsignedShort();
            case "readShortAdd":
                return buffer.readUnsignedShortAdd();
            case "readShortLE":
                return buffer.readUnsignedShortLE();
            case "readShortAddLE":
                return buffer.readUnsignedShortAddLE();
            case "readLengthShort":
                return buffer.readLengthShort();
            case "readMedium":
                return buffer.readMedium();
            case "readInt":
                return buffer.readInt();
            case "readIntME":
                return buffer.readIntME();
            case "readIntLE":
                return buffer.readIntLE();
            case "readIntIME":
                return buffer.readIntIME();
            case "readVarInt":
                return buffer.readVarInt();
            case "readLengthInt":
                return buffer.readLengthInt();
            case "readLong":
                return buffer.readLong();
            case "readFloat":
                return (int) buffer.readFloat();
            default:
                return -1;
        }
    }

    public static boolean isParsableAsNumber(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void fillMaps()
    {
        try
        {
            try (InputStream inputStream = PacketMapReader.class.getResourceAsStream("packets.json")) {
                assert inputStream != null;
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String fileContent = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
                defs = gson.fromJson(fileContent, new TypeToken<ArrayList<MapEntry>>(){}.getType());
            }
        }
        catch (IOException e)
        {
            System.err.println("PacketMapReader::fillMaps // " + e.getMessage());
            System.exit(0);
            defs = new ArrayList<>();
        }
    }
}