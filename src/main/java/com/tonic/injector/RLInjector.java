package com.tonic.injector;

import com.tonic.Main;
import com.tonic.injector.pipeline.StripAnnotationsTransformer;
import com.tonic.injector.rlpipeline.ScheduleWithFixedDelayTransformer;
import com.tonic.model.Artifact;
import com.tonic.util.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;

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
        }

        for (var entry : runelite.entrySet()) {
            Main.LIBS.getRunelite().classes.put(
                    entry.getKey(),
                    ClassNodeUtil.toBytes(entry.getValue())
            );
        }
    }
}
