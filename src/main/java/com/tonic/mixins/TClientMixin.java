package com.tonic.mixins;

import com.tonic.api.*;
import com.tonic.injector.annotations.*;

@Mixin("Client")
public abstract class TClientMixin implements TClient
{
    @Shadow("packetWriter")
    private static TPacketWriter packetWriter;

    @Inject
    @Override
    public TPacketWriter getPacketWriter()
    {
        return packetWriter;
    }

    @Override
    @Construct("ClientPacket")
    public abstract TClientPacket newClientPacket(int id, int length);

    @Shadow("getPacketBufferNode")
    public abstract TPacketBufferNode getPacketBufferNode(TClientPacket clientPacket, TIsaacCipher isaacCipher);

    @Shadow("packedCallStack1")
    private static String packedClassStack1;

    @Shadow("packedCallStack2")
    private static String packedClassStack2;

    @MethodOverride("callStackPacker1")
    @SkipPoison
    public static void callStackPacker1()
    {
        packedClassStack1 = "506+";
    }

    @MethodOverride("callStackPacker2")
    @SkipPoison
    public static void callStackPacker2()
    {
        packedClassStack2 = "704+";
    }

    @MethodOverride("callStackCheck")
    @SkipPoison
    public static String _oe(long l) {
        return "client42918oe\n" +
                "client58307pq\n" +
                "nrc.RuneLite299start\n" +
                "nrc.RuneLite276main\n" +
                "nrl.ReflectionLa+64lambda$launc+\n" +
                "jl.ThreadUnknown +";
    }
}
