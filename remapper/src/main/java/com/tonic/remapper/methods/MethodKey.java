package com.tonic.remapper.methods;

public final class MethodKey {
    public final String owner;
    public final String name;
    public final String desc;
    public boolean hasGarbage = false;

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

    public boolean equals(String owner, String name, String desc) {
        return this.owner.equals(owner) && this.name.equals(name) && this.desc.equals(desc);

    }

    @Override
    public int hashCode() {
        return getLongName().hashCode();
    }

    @Override
    public String toString() {
        return owner + "." + name + desc;
    }
}