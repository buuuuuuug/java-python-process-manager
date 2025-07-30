package com.processmanager.communication;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstract base class for communication channels between Java and Python processes.
 */
public abstract class CommunicationChannel implements Closeable {
    
    protected volatile boolean isOpen = false;
    protected final String channelId;
    
    protected CommunicationChannel(String channelId) {
        this.channelId = channelId;
    }
    
    /**
     * Opens the communication channel.
     * 
     * @throws IOException if channel cannot be opened
     */
    public abstract void open() throws IOException;
    
    /**
     * Sends a framed message through the channel.
     * 
     * @param message The message to send
     * @throws IOException if sending fails
     */
    public abstract void sendMessage(String message) throws IOException;
    
    /**
     * Receives a framed message from the channel.
     * 
     * @return The received message
     * @throws IOException if receiving fails
     */
    public abstract String receiveMessage() throws IOException;
    
    /**
     * Sends raw bytes through the channel.
     * 
     * @param data The data to send
     * @throws IOException if sending fails
     */
    public abstract void sendBytes(byte[] data) throws IOException;
    
    /**
     * Receives raw bytes from the channel.
     * 
     * @param buffer Buffer to read into
     * @return Number of bytes read
     * @throws IOException if receiving fails
     */
    public abstract int receiveBytes(byte[] buffer) throws IOException;
    
    /**
     * Checks if the channel is open and ready for communication.
     * 
     * @return true if channel is open
     */
    public boolean isOpen() {
        return isOpen;
    }
    
    /**
     * Gets the channel identifier.
     * 
     * @return Channel ID
     */
    public String getChannelId() {
        return channelId;
    }
    
    /**
     * Closes the communication channel.
     */
    @Override
    public abstract void close() throws IOException;
}