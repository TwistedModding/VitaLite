package com.tonic.mixins;

import com.tonic.api.*;
import com.tonic.injector.annotations.*;
import com.tonic.services.CallStackFilter;
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
    public static String callStackVerifier(long j)
    {
        try {
            StackTraceElement[] trace = new RuntimeException().getStackTrace();
            StringBuilder filtered = new StringBuilder();
            for (StackTraceElement e : trace) {
                String cls = e.getClassName();
                boolean isMine = false;
                for (String ignored : CallStackFilter.getIgnored()) {
                    if (cls.startsWith(ignored)) {
                        isMine = true;
                        break;
                    }
                }
                if (!isMine) {
                    filtered.append(e).append(",");
                }
            }

            String raw = filtered.toString();

            Pattern framePkg = Pattern.compile("(.)[^.]*\\.");
            Pattern mainPattern = Pattern
                    .compile("\\[?([^,]*/)?([^,]*\\.)?([^.,]*)\\.([^,.]+)\\(([^,:]+:)?([^,:)]*)\\)(, |])");

            Matcher matcher = mainPattern.matcher(raw);
            StringBuilder result = new StringBuilder();

            while (matcher.find()) {
                String pkgPart = matcher.group(2);
                String clsName = matcher.group(3);
                String method  = matcher.group(4);
                String lineNo  = matcher.group(6);

                String pkgPrefix = pkgPart == null
                        ? ""
                        : framePkg.matcher(pkgPart).replaceAll("$1") + ".";

                String replacement = pkgPrefix
                        + truncate(clsName, 12)
                        + lineNo
                        + truncate(method, 12)
                        + "\n";

                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);

            String processed = result.toString();
            return truncate(processed, 119);
        } catch (Exception e) {
            String out = String.valueOf(e);
            System.out.println("ERROR: " + out);
            return out;
        }
    }

    @Inject
    public static String truncate(String string, int n) {
        if (string.length() > n) {
            return string.substring(0, n) + "+";
        }
        return string;
    }
}
