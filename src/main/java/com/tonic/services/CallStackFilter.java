package com.tonic.services;

import com.tonic.classloader.ProxyClassProvider;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public class CallStackFilter
{
    @Getter
    private static Set<String> ignored = new HashSet<>();

    static {
        ignored.add("com.tonic");
        ignored.add(ProxyClassProvider.PROXY_CLASS_PACKAGE);
    }

    public static void processName(String name)
    {
        ignored.add(extractPackage(name));
    }

    private static String extractPackage(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return fullName;
        }
        int idx = fullName.lastIndexOf('.');
        return idx == -1 ? "" : fullName.substring(0, idx);
    }

}
