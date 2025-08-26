package com.tonic.api;

public interface TClient
{
    /**
     * Gets the packet writer for sending packets.
     *
     * @return the packet writer
     */
    TPacketWriter getPacketWriter();
    
    /**
     * Creates a new client packet with the specified id and length.
     *
     * @param id the packet id
     * @param length the packet length
     * @return a new client packet
     */
    TClientPacket newClientPacket(int id, int length);
    
    /**
     * Gets a packet buffer node for the given client packet and cipher.
     *
     * @param clientPacket the client packet
     * @param isaacCipher the Isaac cipher for encryption
     * @return a packet buffer node
     */
    TPacketBufferNode getPacketBufferNode(TClientPacket clientPacket, TIsaacCipher isaacCipher);
    
    /**
     * Gets the current tick count.
     *
     * @return the tick count
     */
    int getTickCount();
    
    /**
     * Sets a varbit value.
     *
     * @param varbit the varbit id
     * @param value the value to set
     */
    void setVarbit(int varbit, int value);
    
    /**
     * Gets a varbit value from the varps array.
     *
     * @param varps the varps array
     * @param varbitId the varbit id
     * @return the varbit value
     */
    int getVarbitValue(int[] varps, int varbitId);
    
    /**
     * Sets a varbit value in the varps array.
     *
     * @param varps the varps array
     * @param varbit the varbit id
     * @param value the value to set
     */
    void setVarbitValue(int[] varps, int varbit, int value);
    
    /**
     * Gets a varp value.
     *
     * @param varpId the varp id
     * @return the varp value
     */
    int getVarpValue(int varpId);
    
    /**
     * Checks if the current thread is the client thread.
     *
     * @return true if on client thread, false otherwise
     */
    boolean isClientThread();
}
