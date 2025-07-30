package com.processmanager.communication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocketChannelTest {
    
    private SocketChannel serverChannel;
    private ExecutorService executor;
    private static final int TEST_PORT = 12345;
    
    @BeforeEach
    void setUp() {
        serverChannel = new SocketChannel("test-channel", TEST_PORT, 5000);
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.close();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Test
    void shouldCreateSocketChannel() {
        // Then
        assertThat(serverChannel.getChannelId()).isEqualTo("test-channel");
        assertThat(serverChannel.getPort()).isEqualTo(TEST_PORT);
        assertThat(serverChannel.getConnectionTimeoutMs()).isEqualTo(5000);
        assertThat(serverChannel.isOpen()).isFalse();
    }
    
    @Test
    void shouldOpenAndCloseChannel() throws Exception {
        // Start server in background
        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                serverChannel.open();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);
        
        // Give server time to start
        Thread.sleep(100);
        
        // Connect client
        try (Socket clientSocket = new Socket("localhost", TEST_PORT)) {
            // Wait for server to accept connection
            serverFuture.get(2, TimeUnit.SECONDS);
            
            // Then
            assertThat(serverChannel.isOpen()).isTrue();
        }
        
        // When
        serverChannel.close();
        
        // Then
        assertThat(serverChannel.isOpen()).isFalse();
    }
    
    @Test
    void shouldSendAndReceiveMessages() throws Exception {
        // Start server
        CompletableFuture<String> serverReceiveFuture = CompletableFuture.supplyAsync(() -> {
            try {
                serverChannel.open();
                return serverChannel.receiveMessage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);
        
        // Give server time to start
        Thread.sleep(100);
        
        // Connect client and send message
        String testMessage = "Hello from client!";
        SocketChannel clientChannel = new SocketChannel("client-channel", 0);
        try {
            clientChannel.openAsClient("localhost", TEST_PORT);
            
            // Send message from client
            clientChannel.sendMessage(testMessage);
            
            // Receive message on server
            String receivedMessage = serverReceiveFuture.get(2, TimeUnit.SECONDS);
            
            // Then
            assertThat(receivedMessage).isEqualTo(testMessage);
        } finally {
            clientChannel.close();
        }
    }
    
    @Test
    void shouldHandleBidirectionalCommunication() throws Exception {
        String clientMessage = "Hello from client!";
        String serverMessage = "Hello from server!";
        
        // Start server
        CompletableFuture<String> serverFuture = CompletableFuture.supplyAsync(() -> {
            try {
                serverChannel.open();
                String received = serverChannel.receiveMessage();
                serverChannel.sendMessage(serverMessage);
                return received;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);
        
        // Give server time to start
        Thread.sleep(100);
        
        // Connect client
        SocketChannel clientChannel = new SocketChannel("client-channel", 0);
        try {
            clientChannel.openAsClient("localhost", TEST_PORT);
            
            // Send message from client
            clientChannel.sendMessage(clientMessage);
            
            // Receive response from server
            String serverResponse = clientChannel.receiveMessage();
            
            // Wait for server to complete
            String clientMessageReceived = serverFuture.get(2, TimeUnit.SECONDS);
            
            // Then
            assertThat(clientMessageReceived).isEqualTo(clientMessage);
            assertThat(serverResponse).isEqualTo(serverMessage);
        } finally {
            clientChannel.close();
        }
    }
    
    @Test
    void shouldThrowExceptionWhenSendingOnClosedChannel() {
        // When/Then
        assertThatThrownBy(() -> serverChannel.sendMessage("test"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Channel is not open");
    }
    
    @Test
    void shouldThrowExceptionWhenReceivingOnClosedChannel() {
        // When/Then
        assertThatThrownBy(() -> serverChannel.receiveMessage())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Channel is not open");
    }
    
    @Test
    void shouldTimeoutOnConnectionWait() {
        // Given
        SocketChannel shortTimeoutChannel = new SocketChannel("timeout-test", TEST_PORT + 1, 100);
        
        // When/Then
        assertThatThrownBy(() -> shortTimeoutChannel.open())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Connection timeout");
    }
    
    @Test
    void shouldHandleLargeMessages() throws Exception {
        // Create large message
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("This is line ").append(i).append(" with some content to make it longer.\n");
        }
        String largeMessage = sb.toString();
        
        // Start server
        CompletableFuture<String> serverFuture = CompletableFuture.supplyAsync(() -> {
            try {
                serverChannel.open();
                return serverChannel.receiveMessage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);
        
        // Give server time to start
        Thread.sleep(100);
        
        // Connect client and send large message
        SocketChannel clientChannel = new SocketChannel("client-channel", 0);
        try {
            clientChannel.openAsClient("localhost", TEST_PORT);
            
            clientChannel.sendMessage(largeMessage);
            
            String receivedMessage = serverFuture.get(5, TimeUnit.SECONDS);
            
            // Then
            assertThat(receivedMessage).isEqualTo(largeMessage);
        } finally {
            clientChannel.close();
        }
    }
}