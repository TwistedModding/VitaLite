package com.tonic.remap;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MethodKey {
    public final String owner;
    public final String name;
    public final String desc;

    public MethodKey(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    public String getLongName() {
        return owner + "." + name + desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodKey)) return false;
        MethodKey that = (MethodKey) o;
        return getLongName().equals(that.getLongName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, desc);
    }

    @Override
    public String toString() {
        return owner + "." + name + desc;
    }
}