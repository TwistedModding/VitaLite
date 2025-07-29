package com.tonic.services.packets.types;

import com.tonic.services.packets.PacketBuffer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public class PacketDefinition
{
    private final String name;
    private final PacketBuffer buffer;
    private final Map<String,Long> map = new HashMap<>();
}