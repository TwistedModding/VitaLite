package com.tonic.dto;

import com.google.gson.annotations.Expose;
import lombok.Data;

@Data
public class JField
{
    @Expose
    private String name;  //mapped name
    @Expose
    private String obfuscatedName;
    @Expose
    private String owner; //mapped owner name
    @Expose
    private String ownerObfuscatedName;
    @Expose
    private String descriptor;
    @Expose
    private Number getter; //ignore for now
    @Expose
    private boolean isStatic;

    private boolean fieldHookAfter = false;

    @Override
    public String toString()
    {
        return name + " [" + ownerObfuscatedName + "." + obfuscatedName + " : " + owner + "." + name + "] " + descriptor;
    }
}