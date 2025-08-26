package com.tonic.rlmixins;

import com.tonic.injector.util.GuiceBindingInjector;
import com.tonic.injector.annotations.ClassMod;
import com.tonic.injector.annotations.MethodOverride;
import com.tonic.injector.annotations.Mixin;
import org.objectweb.asm.tree.ClassNode;

@Mixin("net/runelite/client/RuneLiteModule")
public class RuneLiteModuleMixin
{
    @ClassMod
    public static void init(ClassNode node)
    {
        GuiceBindingInjector.addCastBinding(
                "com/tonic/api/TClient",
                "provideTClient"
        );

        // Add TPacketWriter binding - cast to TClient then call getPacketWriter()
        GuiceBindingInjector.addGetterBinding(
                "com/tonic/api/TPacketWriter",
                "provideTPacketWriter",
                "com/tonic/api/TClient",
                "getPacketWriter",
                "()Lcom/tonic/api/TPacketWriter;"
        );

        // Apply all the bindings to the class
        GuiceBindingInjector.patch(node);
    }

    @MethodOverride("provideTelemetry")
    public Object provideTelemetry() {
        return null;
    }
}
