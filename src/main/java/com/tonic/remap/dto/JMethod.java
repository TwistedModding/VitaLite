package com.tonic.remap.dto;

import com.google.gson.annotations.Expose;
import lombok.Data;

@Data
public class JMethod
{
    @Expose
    private String name; //mapped name
    @Expose
    private String obfuscatedName;
    @Expose
    private String owner; //mapped owner name
    @Expose
    private String ownerObfuscatedName;
    @Expose
    private String descriptor;
    @Expose
    private Number garbageValue; //ignore for now
    @Expose
    private boolean isStatic;

    @Override
    public String toString()
    {
        return name + " [" + ownerObfuscatedName + "." + obfuscatedName + "] " + descriptor;
    }
}