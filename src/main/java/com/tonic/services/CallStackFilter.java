package com.tonic.services;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public class CallStackFilter
{
    @Getter
    private static Set<String> ignored = new HashSet<>();

    static {
        ignored.add("com.tonic");
    }

    public static void processName(String name)
    {
        String pckg = extractPackage(name);
        if(ignored.add(pckg))
        {
            //System.out.println("Processed package to stack-trace ignores: " + pckg);
        }
    }

    private static String extractPackage(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return fullName;
        }
        int idx = fullName.lastIndexOf('.');
        return idx == -1 ? "" : fullName.substring(0, idx);
    }

}
