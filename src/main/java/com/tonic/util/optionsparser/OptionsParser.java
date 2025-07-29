package com.tonic.util.optionsparser;

import com.tonic.util.optionsparser.annotations.CLIArgument;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Getter
public class OptionsParser {
    @CLIArgument(
            name = "rsdump",
            description = "[Optional] Path to dump the gamepack to"
    )
    private String rsDumpPath = null;

    /**
     * Parses the command line arguments and sets the fields of this class accordingly.
     * @param args
     * the command line arguments
     */
    public void parse(String[] args) {
        Map<String, Field> annotatedFields = new HashMap<>();

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
                String argName = arg.substring(1);
                Field field = annotatedFields.get(argName);
                if (field != null)
                {
                    if ((i + 1) < args.length && !args[i + 1].startsWith("-"))
                    {
                        String value = args[++i];
                        setFieldValue(field, value);
                    }
                    else
                    {
                        setFieldValue(field, "true");
                    }
                }
            }
        }
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
}
