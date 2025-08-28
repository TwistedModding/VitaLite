package com.tonic.mixins;

import com.tonic.Logger;
import com.tonic.api.*;
import com.tonic.injector.annotations.*;
import com.tonic.injector.util.ExceptionUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.Random;

@Mixin("Client")
public abstract class TClientMixin implements TClient
{
    @Shadow("packetWriter")
    private static TPacketWriter packetWriter;

    @Shadow(value = "getItemContainer", isRuneLites = true)
    public abstract <T> T getContainer(int id);

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

    @Shadow("mouseLastPressedTimeMillis")
    private static long clientMouseLastPressedMillis;

    @Inject
    public long getClientMouseLastPressedMillis() {
        return clientMouseLastPressedMillis;
    }

    @Inject
    public void setClientMouseLastPressedMillis(long millis) {
        clientMouseLastPressedMillis = millis;
    }

    @Insert(
            method = "processServerPacket",
            at = @At(
                    value = AtTarget.PUTFIELD,
                    owner = "PacketBuffer",
                    target = "offset"
            ),
            ordinal = 2
    )
    public static void processServerPacket(TClient client, TPacketWriter writer)
    {
        //TODO: impl structure of server packet stuff for logging
    }

    @Shadow("packedCallStack1")
    private static String packedClassStack1;

    @Shadow("packedCallStack2")
    private static String packedClassStack2;

    @Inject
    private static String[] generatedPackedPairs;

    @MethodOverride("callStackPacker1")
    @SkipPoison
    public static void callStackPacker1()
    {
        generateNumberPair();
        packedClassStack1 = generatedPackedPairs[0];
    }

    @MethodOverride("callStackPacker2")
    @SkipPoison
    public static void callStackPacker2()
    {
        generateNumberPair();
        packedClassStack2 = generatedPackedPairs[1];
    }

    @MethodOverride("callStackCheck")
    @SkipPoison
    public static String callStackCheck(long l) {
        return "client18126wi\n" +
                "client50773cp\n" +
                "nrc.RuneLite299start\n" +
                "nrc.RuneLite276main\n" +
                "nrl.ReflectionLa+64lambda$launc+\n" +
                "jl.ThreadUnknown +";
    }

    @Inject
    public static void generateNumberPair() {
        if(generatedPackedPairs != null) {
            return;
        }
        Random rand = new Random();

        int firstNum = 137 + rand.nextInt(650);

        int difference;
        double prob = rand.nextDouble();
        if (prob < 0.7) {
            difference = 90;
        } else if (prob < 0.85) {
            difference = 85 + rand.nextInt(7);
        } else {
            difference = rand.nextBoolean() ? 85 : 107;
        }

        int secondNum = firstNum + difference;

        generatedPackedPairs = new String[] {
                firstNum + "+",
                secondNum + "+"
        };
    }

    @FieldHook("MouseHandler_idleCycles")
    public static boolean onIdleCycleSet(int value) {
        return false;
    }

    @Disable("RunException_sendStackTrace")
    @SkipPoison
    public static boolean sendStackTrace(String message, Throwable throwable) {
        if(throwable != null)
        {
            Logger.error(message);
            Logger.error(ExceptionUtil.formatException(throwable));
        }
        return false;
    }

    @Disable("newRunException")
    @SkipPoison
    public static boolean newRunException(Throwable throwable, String message) {
        if((message != null && message.equals("bj.ac()")) || throwable instanceof NullPointerException)
            return true;

        if(throwable != null)
        {
            Logger.error(message);
            Logger.error(ExceptionUtil.formatException(throwable));
        }
        return false;
    }
}
