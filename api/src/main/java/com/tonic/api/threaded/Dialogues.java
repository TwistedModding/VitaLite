package com.tonic.api.threaded;

import com.tonic.api.widgets.DialogueAPI;

public class Dialogues
{
    public static void processDialogues()
    {
        while(DialogueAPI.continueDialogue())
        {
            Delays.tick();
        }
    }

    public static void processDialogues(String... options)
    {
        DialogueNode.get(options).process();
    }

    public static void waitForDialogues()
    {
        while(!DialogueAPI.dialoguePresent())
        {
            Delays.tick();
        }
    }
}
