package com.tonic.api.threaded;

import static com.tonic.api.widgets.DialogueAPI.*;

public class Dialogues
{
    public static void processDialogues()
    {
        while(continueDialogue())
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
        while(!dialoguePresent())
        {
            Delays.tick();
        }
    }

    public static void continueAllDialogue()
    {
        while(true)
        {
            if(!continueDialogue())
            {
                if(!continueQuestHelper())
                {
                    if(!continueMuseumQuiz())
                    {
                        Delays.tick();
                        break;
                    }
                }
            }
            Delays.tick();
        }
    }
}
