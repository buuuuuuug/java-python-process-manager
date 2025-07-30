package com.processmanager.communication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Handles message framing with length prefixes for reliable data transfer.
 * Messages are framed as: [4-byte length][message data]
 */
public class MessageFraming {
    
    private static final int LENGTH_PREFIX_SIZE = 4;
    
    /**
     * Frames a message with length prefix.
     * 
     * @param message The message to frame
     * @return Framed message bytes
     */
    public static byte[] frameMessage(String message) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(LENGTH_PREFIX_SIZE + messageBytes.length);
        
        // Write length prefix (4 bytes, big-endian)
        buffer.putInt(messageBytes.length);
        
        // Write message data
        buffer.put(messageBytes);
        
        return buffer.array();
    }
    
    /**
     * Frames a message with length prefix.
     * 
     * @param messageBytes The message bytes to frame
     * @return Framed message bytes
     */
    public static byte[] frameMessage(byte[] messageBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(LENGTH_PREFIX_SIZE + messageBytes.length);
        
        // Write length prefix (4 bytes, big-endian)
        buffer.putInt(messageBytes.length);
        
        // Write message data
        buffer.put(messageBytes);
        
        return buffer.array();
    }
    
    /**
     * Extracts the message length from a length prefix.
     * 
     * @param lengthPrefixBytes First 4 bytes containing the length
     * @return Message length
     */
    public static int extractMessageLength(byte[] lengthPrefixBytes) {
        if (lengthPrefixBytes.length != LENGTH_PREFIX_SIZE) {
            throw new IllegalArgumentException("Length prefix must be exactly 4 bytes");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(lengthPrefixBytes);
        return buffer.getInt();
    }
    
    /**
     * Unframes a complete framed message.
     * 
     * @param framedMessage The complete framed message
     * @return The original message string
     */
    public static String unframeMessage(byte[] framedMessage) {
        if (framedMessage.length < LENGTH_PREFIX_SIZE) {
            throw new IllegalArgumentException("Framed message too short");
        }
        
        // Extract length
        ByteBuffer buffer = ByteBuffer.wrap(framedMessage, 0, LENGTH_PREFIX_SIZE);
        int messageLength = buffer.getInt();
        
        // Validate length
        if (messageLength < 0 || messageLength > framedMessage.length - LENGTH_PREFIX_SIZE) {
            throw new IllegalArgumentException("Invalid message length: " + messageLength);
        }
        
        // Extract message
        byte[] messageBytes = new byte[messageLength];
        System.arraycopy(framedMessage, LENGTH_PREFIX_SIZE, messageBytes, 0, messageLength);
        
        return new String(messageBytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Unframes a complete framed message and returns raw bytes.
     * 
     * @param framedMessage The complete framed message
     * @return The original message bytes
     */
    public static byte[] unframeMessageBytes(byte[] framedMessage) {
        if (framedMessage.length < LENGTH_PREFIX_SIZE) {
            throw new IllegalArgumentException("Framed message too short");
        }
        
        // Extract length
        ByteBuffer buffer = ByteBuffer.wrap(framedMessage, 0, LENGTH_PREFIX_SIZE);
        int messageLength = buffer.getInt();
        
        // Validate length
        if (messageLength < 0 || messageLength > framedMessage.length - LENGTH_PREFIX_SIZE) {
            throw new IllegalArgumentException("Invalid message length: " + messageLength);
        }
        
        // Extract message
        byte[] messageBytes = new byte[messageLength];
        System.arraycopy(framedMessage, LENGTH_PREFIX_SIZE, messageBytes, 0, messageLength);
        
        return messageBytes;
    }
    
    /**
     * Gets the length prefix size in bytes.
     * 
     * @return Length prefix size (4 bytes)
     */
    public static int getLengthPrefixSize() {
        return LENGTH_PREFIX_SIZE;
    }
    
    /**
     * Validates that a byte array contains a valid framed message.
     * 
     * @param framedMessage The framed message to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidFramedMessage(byte[] framedMessage) {
        try {
            if (framedMessage.length < LENGTH_PREFIX_SIZE) {
                return false;
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(framedMessage, 0, LENGTH_PREFIX_SIZE);
            int messageLength = buffer.getInt();
            
            return messageLength >= 0 && 
                   messageLength <= framedMessage.length - LENGTH_PREFIX_SIZE &&
                   framedMessage.length == LENGTH_PREFIX_SIZE + messageLength;
        } catch (Exception e) {
            return false;
        }
    }
}