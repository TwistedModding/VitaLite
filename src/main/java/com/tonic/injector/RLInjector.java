package com.tonic.injector;

import com.tonic.Main;
import com.tonic.injector.rlpipeline.*;
import com.tonic.util.ClassFileUtil;
import com.tonic.util.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class RLInjector
{
    public static void patch() throws Exception
    {
        HashMap<String, ClassNode> runelite = new HashMap<>();
        for (var entry : Main.LIBS.getRunelite().classes.entrySet()) {
            String name = entry.getKey();
            byte[] bytes = entry.getValue();
            runelite.put(name, ClassNodeUtil.toNode(bytes));
        }

        for(ClassNode node : runelite.values())
        {
            ScheduleWithFixedDelayTransformer.patch(node);
            InjectSideLoadCallTransformer.patch(node);
            NoOpLoadSideLoadPluginsTransformer.patch(node);
            DisableTelemetryTransformer.patch(node);
            PatchDevToolsPluginManagerTransformer.patch(node);
            RuneliteModuleBindingsTransformer.patch(node);
            RuneLiteObjectStoreTransformer.patch(node);
        }

        for (var entry : runelite.entrySet()) {
            String name = entry.getKey();
            if(SignerMapper.shouldIgnore(name))
            {
                System.out.println("Skipping cert-checked class: " + name);
                continue;
            }

            byte[] bytes = ClassNodeUtil.toBytes(entry.getValue());
            Main.LIBS.getRunelite().classes.put(
                    entry.getKey(),
                    bytes
            );

            List<String> toDump = List.of(
                    "net.runelite.client.RuneLite",
                    "net.runelite.client.RuneLiteModule",
                    "net.runelite.client.plugins.PluginManager"
            );
            if(toDump.contains(name))
            {
                ClassFileUtil.writeClass(
                        name,
                        bytes,
                        Path.of("C:/test/dumper/")
                );
            }
        }
    }
}
