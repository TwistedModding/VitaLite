package com.tonic.util.optionsparser;

import com.tonic.util.optionsparser.annotations.CLIArgument;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class OptionsParser {
    @CLIArgument(
            name = "rsdump",
            description = "[Optional] Path to dump the gamepack to"
    )
    private String rsdump = null;

    /**
     * Parses the command line arguments and sets the fields of this class accordingly.
     * @param args
     * the command line arguments
     */
    public String[] parse(String[] args) {
        Map<String, Field> annotatedFields = new HashMap<>();
        List<String> passThruArgs = new ArrayList<>();

        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields)
        {
            CLIArgument annotation = field.getAnnotation(CLIArgument.class);
            if (annotation != null)
            {
                annotatedFields.put(annotation.name(), field);
            }
        }

        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i];

            if (arg.equals("-help"))
            {
                help();
                System.exit(0);
            }

            if (arg.startsWith("-"))
            {
                if ((i + 1) < args.length && !args[i + 1].startsWith("-"))
                {
                    String argName = arg.substring(2);
                    Field field = annotatedFields.get(argName);
                    if(field == null)
                    {
                        passThruArgs.add(args[i]);
                        continue;
                    }
                    String value = args[++i];
                    setFieldValue(field, value);
                }
                else
                {
                    String argName = arg.substring(1);
                    Field field = annotatedFields.get(argName);
                    if (field == null)
                    {
                        passThruArgs.add(args[i]);
                        continue;
                    }
                    setFieldValue(field, "true");
                }
            }
        }
        for (String arg : passThruArgs)
        {
            System.out.println("Passing to RL: " + arg);
        }
        return passThruArgs.toArray(new String[0]);
    }

    private void help()
    {
        System.out.println("Usage: java -jar deobber.jar [options]");
        System.out.println("Options:");
        for (Field field : this.getClass().getDeclaredFields())
        {
            CLIArgument annotation = field.getAnnotation(CLIArgument.class);
            if (annotation != null)
            {
                System.out.println("  -" + annotation.name() + ": " + annotation.description());
            }
        }
    }

    /**
     * Helper method to set a field's value based on its type.
     */
    private void setFieldValue(Field field, String value) {
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();

            if (type.equals(String.class))
            {
                field.set(this, value);
            }
            else if (type.equals(int.class) || type.equals(Integer.class))
            {
                field.set(this, Integer.valueOf(value));
            }
            else if (type.equals(boolean.class) || type.equals(Boolean.class))
            {
                field.set(this, Boolean.valueOf(value));
            }
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }
    }

    public String[] filter(String[] args)
    {
        Map<String, Field> annotatedFields = new HashMap<>();
        List<String> newArgs = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields())
        {
            CLIArgument annotation = field.getAnnotation(CLIArgument.class);
            if(annotation == null)
                continue;
        }

        Field field;
        for(int i = 0; i < args.length; i++)
        {
            String name = args[i];
            if(!name.startsWith("-") && !name.startsWith("--") && name.equals("-help"))
            {
                newArgs.add(name);
                continue;
            }
            name = name.startsWith("--") ? name.substring(2) : name.substring(1);
            field = findField(name);
            if(field == null)
            {
                newArgs.add(name);
                continue;
            }

            CLIArgument annotation = field.getAnnotation(CLIArgument.class);
            if(annotation == null)
            {
                newArgs.add(name);
                continue;
            }

            if(field.getType() != boolean.class)
            {
                i++;
            }
        }

        return newArgs.toArray(new String[0]);
    }

    private Field findField(String name)
    {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields)
        {
            CLIArgument annotation = field.getAnnotation(CLIArgument.class);
            if (annotation != null && annotation.name().equals(name))
            {
                return field;
            }
        }
        return null;
    }
}
