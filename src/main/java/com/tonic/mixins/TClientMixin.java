package com.tonic.mixins;

import com.tonic.api.*;
import com.tonic.injector.annotations.*;
import com.tonic.services.CallStackFilter;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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


    @MethodOverride("callStackCheck")
    @SkipPoison()
    public static String _oe(long l) {
        try {
            Pattern pattern = Pattern.compile("(.)[^.]*\\.");
            String string = Pattern.compile("\\[?([^,]*/)?([^,]*\\.)?([^.,]*)\\.([^,.]+)\\(([^,:]+:)?([^,:)]*)\\)(, |\\])").matcher(Arrays.toString(new RuntimeException().getStackTrace())).replaceAll(matchResult -> {
                String packageName = matchResult.group(2);

                switch (packageName == null ? -1 : packageName.hashCode()) {
                    case -688050619: //java.lang.reflect.
                    case 575645442:  //java.lang.invoke.
                    case 1227444965: //jdk.internal.reflect.
                    case 1675929244: // ???
                    {
                        return "";
                    }
                }

                if(CallStackFilter.shouldFilter(packageName)) {
                    return "";
                }

                String abbreviated = packageName == null ? "" : pattern.matcher(matchResult.group(2)).replaceAll("$1") + ".";

                String className = matchResult.group(3);
                if (className != null && className.length() > 12) {
                    className = className.substring(0, 12) + "+";
                }

                String methodName = matchResult.group(4);
                if (methodName != null && methodName.length() > 12) {
                    methodName = methodName.substring(0, 12) + "+";
                }

                return Matcher.quoteReplacement(abbreviated + className + matchResult.group(6) + methodName + "\n");
            });

            if (string.length() > 119) {
                return string.substring(0, 119) + "+";
            }

            return string;

        } catch (Exception exception) {
            throw new RuntimeException();
        }
    }
}
