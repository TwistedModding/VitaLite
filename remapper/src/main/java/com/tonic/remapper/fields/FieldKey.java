package com.tonic.remapper.fields;

import java.util.Objects;

public final class FieldKey {
    public final String owner; // internal name
    public final String name;
    public final String desc;

    public FieldKey(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldKey fieldKey = (FieldKey) o;
        return owner.equals(fieldKey.owner) && name.equals(fieldKey.name) && desc.equals(fieldKey.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, desc);
    }

    @Override
    public String toString() {
        return owner + "." + name + " " + desc;
    }
}
