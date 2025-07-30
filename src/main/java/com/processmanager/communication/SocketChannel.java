package com.processmanager.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Socket-based communication channel for cross-platform compatibility.
 */
public class SocketChannel extends CommunicationChannel {
    
    private static final Logger logger = LoggerFactory.getLogger(SocketChannel.class);
    
    private final int port;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final int connectionTimeoutMs;
    
    public SocketChannel(String channelId, int port) {
        this(channelId, port, 30000); // 30 second default timeout
    }
    
    public SocketChannel(String channelId, int port, int connectionTimeoutMs) {
        super(channelId);
        this.port = port;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    @Override
    public void open() throws IOException {
        try {
            // Create server socket
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(connectionTimeoutMs);
            
            logger.info("Socket channel listening on port: {}", port);
            
            // Wait for client connection
            clientSocket = serverSocket.accept();
            
            // Set up streams
            inputStream = clientSocket.getInputStream();
            outputStream = clientSocket.getOutputStream();
            
            isOpen = true;
            logger.info("Socket channel connected: {}", channelId);
            
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timeout after " + connectionTimeoutMs + "ms", e);
        } catch (IOException e) {
            isOpen = false;
            throw new IOException("Failed to open socket channel on port " + port, e);
        }
    }
    
    /**
     * Opens as a client connection to an existing server.
     * 
     * @param host Server host
     * @param serverPort Server port
     * @throws IOException if connection fails
     */
    public void openAsClient(String host, int serverPort) throws IOException {
        try {
            clientSocket = new Socket(host, serverPort);
            inputStream = clientSocket.getInputStream();
            outputStream = clientSocket.getOutputStream();
            
            isOpen = true;
            logger.info("Socket channel connected as client to {}:{}", host, serverPort);
            
        } catch (IOException e) {
            isOpen = false;
            throw new IOException("Failed to connect to " + host + ":" + serverPort, e);
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
        
        if (messageLength < 0 || messageLength > 1024 * 1024) { // 1MB limit
            throw new IOException("Invalid message length: " + messageLength);
        }
        
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
        
        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.warn("Error closing client socket: {}", e.getMessage());
            }
        }
        
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warn("Error closing server socket: {}", e.getMessage());
            }
        }
        
        logger.info("Socket channel closed: {}", channelId);
    }
    
    /**
     * Gets the port number.
     * 
     * @return Port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Gets the connection timeout in milliseconds.
     * 
     * @return Connection timeout
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
}