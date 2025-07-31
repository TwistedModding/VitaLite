package com.tonic.injector;

import com.tonic.Main;
import com.tonic.injector.rlpipeline.InjectSideLoadCallTransformer;
import com.tonic.injector.rlpipeline.NoOpLoadSideLoadPluginsTransformer;
import com.tonic.injector.rlpipeline.ScheduleWithFixedDelayTransformer;
import com.tonic.util.ClassFileUtil;
import com.tonic.util.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.HashMap;

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
        }

        for (var entry : runelite.entrySet()) {
            byte[] bytes = ClassNodeUtil.toBytes(entry.getValue());
            Main.LIBS.getRunelite().classes.put(
                    entry.getKey(),
                    bytes
            );
            if(entry.getKey().equals("net.runelite.client.RuneLite"))
            {
                ClassFileUtil.writeClass(
                        "net.runelite.client.RuneLite",
                        bytes,
                        Path.of("C:\\test\\dumper\\")
                );
            }
        }
    }
}
