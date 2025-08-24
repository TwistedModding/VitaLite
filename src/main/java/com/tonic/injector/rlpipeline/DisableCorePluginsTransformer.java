package com.tonic.injector.rlpipeline;

import com.tonic.vitalite.Main;
import com.tonic.model.ConditionType;
import com.tonic.injector.util.BytecodeBuilder;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class DisableCorePluginsTransformer
{
    private static List<String> allowed = new ArrayList<>()
    {{
        add("net.runelite.client.plugins.config");
        add("net.runelite.client.plugins.lowmemory");
        add("net.runelite.client.plugins.loginscreen");
    }};

    public static void patch(ClassNode classNode) {
        if (!classNode.name.equals("net/runelite/client/plugins/PluginManager"))
            return;

        if(!Main.optionsParser.isNoPlugins())
            return;

        MethodNode loadCorePlugins = classNode.methods.stream()
                .filter(method -> method.name.equals("loadCorePlugins"))
                .findFirst()
                .orElse(null);

        if (loadCorePlugins == null) {
            System.out.println("Failed to find RuneLite loadCorePlugins method");
            return;
        }

        AbstractInsnNode injectionPoint = null;

        for (AbstractInsnNode insn : loadCorePlugins.instructions) {
            //look for the first ASTORE in slot 2
            if (insn.getOpcode() == ASTORE && ((VarInsnNode) insn).var == 2) {
                injectionPoint = insn;
                break;
            }
        }

        if (injectionPoint == null) {
            System.out.println("Failed to find ASTORE in slot 2 in loadCorePlugins method");
            return;
        }

        // Generate the instructions to filter out unwanted plugins
        InsnList insnList = generateInsns();
        // Insert the generated instructions before the ASTORE in slot 2
        loadCorePlugins.instructions.insert(injectionPoint, insnList);
    }

    private static InsnList generateInsns()
    {
        BytecodeBuilder builder = BytecodeBuilder.create()
                .newInstance("java/util/ArrayList")
                .dup()
                .invokeSpecial("java/util/ArrayList", "<init>", "()V")
                .storeLocal(3, ASTORE)
                .loadLocal(2, ALOAD)
                .invokeInterface("java/util/List", "iterator", "()Ljava/util/Iterator;")
                .storeLocal(4, ASTORE)
                .whileBlock(
                        ConditionType.EQUALS,
                        condition -> condition.loadLocal(4, ALOAD)
                                .invokeInterface("java/util/Iterator", "hasNext", "()Z")
                                .pushInt(0),
                        body -> {
                            body.loadLocal(4, ALOAD)
                                    .invokeInterface("java/util/Iterator", "next", "()Ljava/lang/Object;")
                                    .castToType("java/lang/Class")
                                    .storeLocal(5, ASTORE)
                                    .loadLocal(5, ALOAD)
                                    .invokeVirtual("java/lang/Class", "getName", "()Ljava/lang/String;")
                                    .storeLocal(6, ASTORE);
                            LabelNode skipAdd = body.createLabel("skipAdd");
                            body.pushInt(0);
                            for(String prefix : allowed)
                            {
                                body.loadLocal(6, ALOAD)
                                        .pushString(prefix)
                                        .invokeVirtual("java/lang/String", "startsWith", "(Ljava/lang/String;)Z")
                                        .or();
                            }
                            body.pushInt(0)
                                    .jumpIf(ConditionType.EQUALS, skipAdd)
                                    .loadLocal(3, ALOAD)
                                    .loadLocal(5, ALOAD)
                                    .invokeInterface("java/util/List", "add", "(Ljava/lang/Object;)Z")
                                    .pop()
                                    .placeLabel(skipAdd);
                        }
                )
                .loadLocal(3, ALOAD)
                .storeLocal(2, ASTORE);

        return builder.build();
    }
}
