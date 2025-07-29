package com.tonic.injector;

import com.tonic.injector.rlpipeline.ScheduleWithFixedDelayTransformer;
import com.tonic.util.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;

public class RLInjector
{
    public static byte[] patch(String name, byte[] bytes)
    {
        if(bytes == null || !name.startsWith("net.runelite") || name.startsWith("net.runelite.client.ui."))
            return bytes;

        ClassNode classNode = ClassNodeUtil.toNode(bytes);

        ScheduleWithFixedDelayTransformer.patch2(classNode);

        try
        {
            return ClassNodeUtil.toBytes(classNode);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return bytes;
        }
    }
}
