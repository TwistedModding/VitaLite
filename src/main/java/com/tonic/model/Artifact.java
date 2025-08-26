package com.tonic.model;

import java.util.HashMap;

public class Artifact {
    public HashMap<String, byte[]> classes;

    public Artifact() {
        this.classes = new HashMap<>(16, 0.5f);
    }
}
