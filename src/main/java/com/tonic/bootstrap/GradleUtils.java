package com.tonic.bootstrap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleUtils
{
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"].*$");

    public static String extractVersion(String gradleFile)
    {
        try (var lines = gradleFile.lines())
        {
            for (String line : (Iterable<String>) lines::iterator)
            {
                Matcher m = VERSION_PATTERN.matcher(line);
                if (m.matches())
                {
                    return m.group(1);
                }
            }
        }

        return null;
    }
}