package com.tonic.remapper.buffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BufferDef
{
    public static final Map<String, String> signatures = new HashMap<>()
    {{
        put("{V}[0][0][~127][~2047][~1][+][~1][-][0][0][STORE][0][~127][0][STORE][~2047][~192][~6][6][STORE][~128][~63][0][STORE][~224][~12][12][STORE][~128][~6][6][~63][STORE][~128][~63][0][STORE][-][+]", "writeCESU8");
        put("{V}[~1][+][~1][-][~128][+][0][STORE][~1][+][~1][-][~8][8][STORE]", "writeShortAdd");
        put("{V}[-][~2][-][~8][8][STORE][-][~1][-][0][STORE]", "writeLengthShort");
        put("[~4][+][~4][-][~255][~3][-][~255][~8][8][+][~2][-][~255][~16][16][+][~1][-][~255][~24][24][+]", "readUnsignedIntLE");
        put("[~4][+][~3][-][~255][~16][16][~1][-][~255][+][~2][-][~255][~8][8][+][~4][-][~255][~24][24][+]", "readInt");
        put("[~1][+][~1][-][~128][-]", "readByteAdd");
        put("[~1][+][~1][-][+][+]", "readCESU8");
        put("{V}[~1][+][~1][-][0][STORE]", "writeByte");
        put("{V}[~1][+][~1][-][~16][16][STORE][~1][+][~1][-][~8][8][STORE][~1][+][~1][-][0][STORE]", "writeMedium");
        put("[~255][~128][-]", "readUShortSmart");
        put("[0][~1][+][~1][-][-][~255]", "readUnsignedByteNeg");
        put("[0][~32767][+]", "readIncrSmallSmart");
        //put("{V}{[B}{3}[+][~1][+][~1][-][0][STORE]", "writeBytes");
        put("[~3][+][~3][-][~255][~16][16][~1][-][~255][+][~2][-][~255][~8][8][+]", "readMedium");
        put("[~2][+][~1][-][~128][-][~255][~2][-][~255][~8][8][+]", "readUnsignedShortAdd");
        put("{V}[0][~1][+][~1][-][0][0][STORE][0][+][~1][+][~1][-][0][0][STORE]", "writeStringCp1252NullCircumfixed");
        put("{V}[-128][-16384][~28][~128][~21][~128][~14][~128][~7][~128][~127]", "writeVarInt");
        put("[~1][+][~1][-][~1][+][~1][-][-][~1][-]", "readStringCp1252NullCircumfixed");
        put("{V}[~1][+][~1][-][~8][8][STORE][~1][+][~1][-][0][STORE]", "writeShort");
        put("[~2][+][~1][-][~255][~2][-][~255][~8][8][+]", "readUnsignedShort");
        put("[~128][~1][+][~1][-][-]", "readByteSub");
        put("{V}[~1][0]", "writeBoolean");
        put("[~1][+][~1][-][0][~127][~7][7][~1][+][~1][-]", "readVarInt");
        put("[~2][+][~1][-][~255][~8][8][~2][-][~128][-][~255][+][~32767][-]", "readSignedShort");
        put("{V}[~1][+][~1][-][0][-][0][STORE]", "writeByteNeg");
        put("{V}[~1][+][~1][-][0][STORE][~1][+][~1][-][~8][8][STORE]", "writeShortLE");
        put("{V}[0]", "writeBuffer");
        put("[~4][+][~2][-][~255][~24][24][~4][-][~255][~8][8][+][~3][-][~255][+][~1][-][~255][~16][16][+]", "readUnsignedIntIME");
        put("{V}[~1][+][~1][-][~8][8][STORE][~1][+][~1][-][~128][+][0][STORE]", "writeShortAddLE");
        put("[~32767]", "readNullableLargeSmart");
        put("{V}[~1][+][~1][-][~128][+][0][STORE]", "writeByteAdd");
        put("{V}[~1][+][~1][-][~56][0][STORE][~1][+][~1][-][~48][0][STORE][~1][+][~1][-][~40][0][STORE][~1][+][~1][-][~32][0][STORE][~1][+][~1][-][~24][0][STORE][~1][+][~1][-][~16][0][STORE][~1][+][~1][-][~8][0][STORE][~1][+][~1][-][0][STORE]", "writeLong");
        put("[~255][~128][~1][-][-]", "readShortSmartSub");
        put("{V}[~1][+][~1][-][0][STORE][~1][+][~1][-][~8][8][STORE][~1][+][~1][-][~16][16][STORE][~1][+][~1][-][~24][24][STORE]", "writeIntLE");
        put("[~1][+][~1][-][-][~1][-]", "readStringCp1252NullTerminated");
        put("[~2][+][~1][-][~255][~8][8][~2][-][~255][+]", "readUnsignedShortLE");
        put("{V}[~1][+][~1][-][~16][16][STORE][~1][+][~1][-][~24][24][STORE][~1][+][~1][-][0][STORE][~1][+][~1][-][~8][8][STORE]", "writeIntIME");
        put("[~1][+][~1][-][~128][-][~255]", "readUnsignedByteAdd");
        put("[~2][+][~1][-][~255][~8][8][~2][-][~255][+][~32767][-]", "readShortLE");
        put("[~1][+][~1][-][~255]", "readUnsignedByte");
        put("{V}[-][~4][-][~24][24][STORE][-][~3][-][~16][16][STORE][-][~2][-][~8][8][STORE][-][~1][-][0][STORE]", "writeLengthInt");
        put("{V}[~1][+][~1][-][~24][24][STORE][~1][+][~1][-][~16][16][STORE][~1][+][~1][-][~8][8][STORE][~1][+][~1][-][0][STORE]", "writeInt");
        put("[~2][+][~1][-][~255][~8][8][~2][-][~128][-][~255][+]", "readUnsignedShortAddLE");
        put("{V}[~128][+]", "writeSmartByteShort");
        put("{V}[~1][+][~1][-][~40][0][STORE][~1][+][~1][-][~32][0][STORE][~1][+][~1][-][~24][0][STORE][~1][+][~1][-][~16][0][STORE][~1][+][~1][-][~8][0][STORE][~1][+][~1][-][0][STORE]", "writeLongMedium");
        put("[0][~1][+][~1][-][-]", "readByteNeg");
        put("{V}[~1][+][~1][-][~128][-][0][STORE]", "writeByteSub");
        put("[~1][+]", "readStringCp1252NullTerminatedOrNull");
        put("[~32]", "readLong");
        put("[~255][~128][~64][-][-]", "readShortSmart");
        put("{V}[~1][+][~1][-][~8][8][STORE][~1][+][~1][-][0][STORE][~1][+][~1][-][~24][24][STORE][~1][+][~1][-][~16][16][STORE]", "writeIntME");
        put("[~2][+][~1][-][~255][~2][-][~255][~8][8][+][~32767][-]", "readShort");
        put("{V}[0][0][+][~1][+][~1][-][0][0][STORE]", "writeStringCp1252NullTerminated");
        put("{V}[~255][-][~1][-][0][STORE]", "writeLengthByte");
        //put("{V}{[B}{3}[+][~1][+][~1][-][0][STORE]", "readBytes");
        put("[~4][+][~1][-][~255][~8][8][~4][-][~255][~16][16][+][~2][-][~255][+][~3][-][~255][~24][24][+]", "readUnsignedIntME");
        put("[~128][~1][+][~1][-][-][~255]", "readUnsignedByteSub");
        put("[~1][~1][~1][0]", "readBoolean");
        put("[~1][+][~1][-]", "readByte");
    }};

    public static void checkAndPrintDuplicateValues() {
        if (signatures == null || signatures.isEmpty()) {
            System.out.println("Map is empty or null.");
            return;
        }

        // Group keys by their values
        Map<String, List<String>> valueToKeys = new HashMap<>();

        for (Map.Entry<String, String> entry : signatures.entrySet()) {
            valueToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        // Find and count duplicates
        List<Map.Entry<String, List<String>>> duplicates = valueToKeys.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size())) // Sort by most duplicates first
                .collect(Collectors.toList());

        if (duplicates.isEmpty()) {
            System.out.println("+ No duplicate values found in the map.");
            return;
        }

        // Print header
        System.out.println("===============================================");
        System.out.println("       DUPLICATE VALUES DETECTED");
        System.out.println("===============================================");
        System.out.println();

        int groupNumber = 1;

        for (Map.Entry<String, List<String>> entry : duplicates) {
            String value = entry.getKey();
            List<String> keys = entry.getValue();

            System.out.println("Group " + groupNumber + ":");
            System.out.println("+- Shared Value: \"" + value + "\"");
            System.out.println("+- Number of Keys: " + keys.size());
            System.out.println("+- Keys:");

            for (int i = 0; i < keys.size(); i++) {
                String prefix = (i == keys.size() - 1) ? "     +-- " : "     +-- ";
                System.out.println(prefix + "\"" + keys.get(i) + "\"");
            }

            System.out.println();
            groupNumber++;
        }

        // Print summary
        System.out.println("-----------------------------------------------");
        System.out.println("Summary:");
        System.out.println("• Total duplicate groups: " + duplicates.size());
        System.out.println("• Total keys with shared values: " +
                duplicates.stream().mapToInt(e -> e.getValue().size()).sum());
        System.out.println("-----------------------------------------------");
    }
}
