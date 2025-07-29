package com.tonic.model;

import java.util.HashMap;

public class Artifact {
    // Name of the artifact jar
    public String artifactName;

    // HashMap of class FQDN -> class bytes
    public HashMap<String, byte[]> classes;

    public Artifact() {
        this.classes = new HashMap<>();
    }
}
