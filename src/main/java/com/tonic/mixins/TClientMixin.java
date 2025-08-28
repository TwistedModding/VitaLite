package com.tonic.mixins;

import com.tonic.Logger;
import com.tonic.api.*;
import com.tonic.injector.annotations.*;
import com.tonic.injector.util.ExceptionUtil;

@Mixin("Client")
public abstract class TClientMixin implements TClient
{
    @Shadow("packetWriter")
    private static TPacketWriter packetWriter;

    @Shadow("MouseHandler_instance")
    private static TMouseHandler mouseHandler;

    @Inject
    @Override
    public TPacketWriter getPacketWriter()
    {
        return packetWriter;
    }

    @Inject
    @Override
    public TMouseHandler getMouseHandler()
    {
        return mouseHandler;
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
