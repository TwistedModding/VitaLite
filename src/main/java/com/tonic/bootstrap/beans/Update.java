package com.tonic.bootstrap.beans;

import lombok.Data;

@Data
public class Update
{
    private String os; // windows, macos
    private String osName; // os.name
    private String osVersion; // os.version
    private String arch; // os.arch
    private String name; // update name
    private String version; // update version
    private String minimumVersion; // minimum launcher version to update
    private String url;
    private String hash;
    private int size;
    private double rollout;
}