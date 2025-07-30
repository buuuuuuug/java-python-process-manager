package com.processmanager.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Named pipe communication channel for Unix-like systems.
 */
public class NamedPipeChannel extends CommunicationChannel {
    
    private static final Logger logger = LoggerFactory.getLogger(NamedPipeChannel.class);
    
    private final Path pipePath;
    private FileInputStream inputStream;
    private FileOutputStream outputStream;
    
    public NamedPipeChannel(String channelId, String pipePath) {
        super(channelId);
        this.pipePath = Paths.get(pipePath);
    }
    
    @Override
    public void open() throws IOException {
        try {
            // Create named pipe if it doesn't exist
            if (!Files.exists(pipePath)) {
                createNamedPipe();
            }
            
            // Open streams
            outputStream = new FileOutputStream(pipePath.toFile());
            inputStream = new FileInputStream(pipePath.toFile());
            
            isOpen = true;
            logger.info("Named pipe channel opened: {}", pipePath);
            
        } catch (IOException e) {
            isOpen = false;
            throw new IOException("Failed to open named pipe channel: " + pipePath, e);
        }
    }
    
    private void createNamedPipe() throws IOException {
        try {
            // Use mkfifo command to create named pipe
            ProcessBuilder pb = new ProcessBuilder("mkfifo", pipePath.toString());
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new IOException("mkfifo command failed with exit code: " + exitCode);
            }
            
            logger.debug("Created named pipe: {}", pipePath);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while creating named pipe", e);
        }
    }
    
    @Override
    public void sendMessage(String message) throws IOException {
        if (!isOpen) {
            throw new IOException("Channel is not open");
        }
        
        byte[] framedMessage = MessageFraming.frameMessage(message);
        sendBytes(framedMessage);
    }
    
    @Override
    public String receiveMessage() throws IOException {
        if (!isOpen) {
            throw new IOException("Channel is not open");
        }
        
        // Read length prefix first
        byte[] lengthBytes = new byte[MessageFraming.getLengthPrefixSize()];
        int bytesRead = 0;
        
        while (bytesRead < lengthBytes.length) {
            int read = inputStream.read(lengthBytes, bytesRead, lengthBytes.length - bytesRead);
            if (read == -1) {
                throw new IOException("End of stream reached while reading length prefix");
            }
            bytesRead += read;
        }
        
        // Extract message length
        int messageLength = MessageFraming.extractMessageLength(lengthBytes);
        
        // Read message data
        byte[] messageBytes = new byte[messageLength];
        bytesRead = 0;
        
        while (bytesRead < messageLength) {
            int read = inputStream.read(messageBytes, bytesRead, messageLength - bytesRead);
            if (read == -1) {
                throw new IOException("End of stream reached while reading message data");
            }
            bytesRead += read;
        }
        
        return new String(messageBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
    
    @Override
    public void sendBytes(byte[] data) throws IOException {
        if (!isOpen) {
            throw new IOException("Channel is not open");
        }
        
        outputStream.write(data);
        outputStream.flush();
    }
    
    @Override
    public int receiveBytes(byte[] buffer) throws IOException {
        if (!isOpen) {
            throw new IOException("Channel is not open");
        }
        
        return inputStream.read(buffer);
    }
    
    @Override
    public void close() throws IOException {
        isOpen = false;
        
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                logger.warn("Error closing input stream: {}", e.getMessage());
            }
        }
        
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                logger.warn("Error closing output stream: {}", e.getMessage());
            }
        }
        
        // Clean up the named pipe file
        try {
            if (Files.exists(pipePath)) {
                Files.delete(pipePath);
                logger.debug("Deleted named pipe: {}", pipePath);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete named pipe {}: {}", pipePath, e.getMessage());
        }
        
        logger.info("Named pipe channel closed: {}", pipePath);
    }
    
    /**
     * Gets the pipe path.
     * 
     * @return Path to the named pipe
     */
    public Path getPipePath() {
        return pipePath;
    }
}