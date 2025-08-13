package com.tonic.injector.rlpipeline;

import com.tonic.injector.GuiceBindingInjector;
import org.objectweb.asm.tree.ClassNode;

public class RuneliteModuleBindingsTransformer {

    public static void patch(ClassNode classNode){
        if(!classNode.name.equals("net/runelite/client/RuneLiteModule"))
            return;

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
        GuiceBindingInjector.patch(classNode);
    }
}
