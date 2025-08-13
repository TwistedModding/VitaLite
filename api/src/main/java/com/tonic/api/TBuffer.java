package com.tonic.api;

public interface TBuffer
{
    byte[] getArray();
    int getOffset();
    //bytes
    void writeByte(int var);
    void writeByteAdd(int var);
    void writeByteNeg(int var);
    void writeByteSub(int var);

    //shorts
    void writeShort(int var);
    void writeShortLE(int var);
    void writeShortAdd(int var);
    void writeShortAddLE(int var);

    //ints
    void writeIntME(int var);
    void writeIntLE(int var);
    void writeInt(int var);
    void writeIntIME(int var);

    //string stuff
    void writeLengthByte(int var);
    void writeStringCp1252NullTerminated(String var);
    void writeStringCp1252NullCircumfixed(String var);
}
