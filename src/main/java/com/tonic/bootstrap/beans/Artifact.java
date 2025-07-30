package com.tonic.bootstrap.beans;

import lombok.Data;

@Data
public class Artifact
{
    private String name;
    private String path;
    private String hash;
    private int size;
    private Diff[] diffs;
    private Platform[] platform;
}