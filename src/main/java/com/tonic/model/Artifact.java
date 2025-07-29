package com.tonic.model;

import java.util.HashMap;

public class Artifact {
    public HashMap<String, byte[]> classes;

    public Artifact() {
        this.classes = new HashMap<>();
    }
}
