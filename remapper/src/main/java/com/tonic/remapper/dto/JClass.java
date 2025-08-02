package com.tonic.remapper.dto;

import com.google.gson.annotations.Expose;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class JClass
{
    @Expose
    private String name; //mapped name
    @Expose
    private String obfuscatedName;
    @Expose
    private final List<JField> fields = new ArrayList<>();
    @Expose
    private final List<JMethod> methods = new ArrayList<>();

    @Override
    public String toString()
    {
        return name + " [" + obfuscatedName + ".class] fields: " + fields.size() + ", methods: " + methods.size();
    }
}