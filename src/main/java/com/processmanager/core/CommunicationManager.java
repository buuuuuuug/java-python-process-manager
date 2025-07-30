package com.processmanager.core;

/**
 * Core interface for managing communication between Java and Python processes.
 * Handles bidirectional message exchange and channel management.
 */
public interface CommunicationManager {
    
    /**
     * Establishes a communication channel with a process.
     * 
     * @param handle Process handle
     * @throws CommunicationException if channel establishment fails
     */
    void establishChannel(ProcessHandle handle);
    
    /**
     * Sends a message to a Python process.
     * 
     * @param handle Process handle
     * @param data Data to send
     * @throws CommunicationException if sending fails
     */
    void sendMessage(ProcessHandle handle, Object data);
    
    /**
     * Receives a message from a Python process.
     * 
     * @param handle Process handle
     * @param type Expected message type
     * @return Received message
     * @throws CommunicationException if receiving fails
     */
    <T> T receiveMessage(ProcessHandle handle, Class<T> type);
    
    /**
     * Closes the communication channel with a process.
     * 
     * @param handle Process handle
     */
    void closeChannel(ProcessHandle handle);
}