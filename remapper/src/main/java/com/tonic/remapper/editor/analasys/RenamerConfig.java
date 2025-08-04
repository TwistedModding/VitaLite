package com.tonic.remapper.editor.analasys;

import lombok.Getter;

@Getter
public class RenamerConfig {
    private boolean addAnnotations = true;
    private String classPrefix = "class";
    private String methodPrefix = "method";
    private String fieldPrefix = "field";

    public RenamerConfig withAnnotations(boolean add) {
        this.addAnnotations = add;
        return this;
    }

    public RenamerConfig withClassPrefix(String prefix) {
        this.classPrefix = prefix;
        return this;
    }

    public RenamerConfig withMethodPrefix(String prefix) {
        this.methodPrefix = prefix;
        return this;
    }

    public RenamerConfig withFieldPrefix(String prefix) {
        this.fieldPrefix = prefix;
        return this;
    }
}
