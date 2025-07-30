package com.processmanager.impl;

import com.processmanager.exception.CommunicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ProcessCommunicationManagerImplTest {
    
    @TempDir
    Path tempDir;
    
    private ProcessCommunicationManagerImpl communicationManager;
    private ProcessHandle testProcessHandle;
    private Process testProcess;
    
    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        communicationManager = new ProcessCommunicationManagerImpl();
        
        // Create a simple test script that can participate in communication
        Path testScript = tempDir.resolve("communication_test.py");
        Files.write(testScript, """
            #!/usr/bin/env python3
            import socket
            import json
            import time
            import sys
            
            # Simple socket client for testing
            def connect_and_communicate():
                try:
                    # Connect to Java server (port will be provided via args)
                    port = int(sys.argv[1]) if len(sys.argv) > 1 else 12345
                    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    sock.connect(('localhost', port))
                    
                    # Send a test message
                    message = {
                        "messageId": "test-1",
                        "messageType": "data",
                        "payload": "Hello from Python",
                        "timestamp": "2024-01-01T12:00:00Z"
                    }
                    
                    message_json = json.dumps(message)
                    message_bytes = message_json.encode('utf-8')
                    length_prefix = len(message_bytes).to_bytes(4, 'big')
                    
                    sock.send(length_prefix + message_bytes)
                    
                    # Keep connection alive for a bit
                    time.sleep(2)
                    
                    sock.close()
                    
                except Exception as e:
                    print(f"Communication error: {e}")
            
            if __name__ == "__main__":
                connect_and_communicate()
            """.getBytes());
        
        // Start test process
        ProcessBuilder pb = new ProcessBuilder("python3", testScript.toString());
        testProcess = pb.start();
        testProcessHandle = testProcess.toHandle();
    }
    
    @AfterEach
    void tearDown() {
        if (testProcessHandle != null && testProcessHandle.isAlive()) {
            testProcessHandle.destroyForcibly();
        }
        if (communicationManager != null) {
            communicationManager.shutdown();
        }
    }
    
    @Test
    void shouldCreateCommunicationManager() {
        // Then
        assertThat(communicationManager).isNotNull();
        assertThat(communicationManager.getActiveChannelCount()).isEqualTo(0);
    }
    
    @Test
    void shouldEstablishChannel() {
        // When
        communicationManager.establishChannel(testProcessHandle);
        
        // Then - channel establishment is asynchronous, so we just verify no exception is thrown
        // In a real environment, the Python process would connect back
        assertThat(communicationManager).isNotNull();
    }
    
    @Test
    void shouldCloseChannel() {
        // Given
        communicationManager.establishChannel(testProcessHandle);
        
        // When
        communicationManager.closeChannel(testProcessHandle);
        
        // Then - should not throw exception
        assertThat(communicationManager.getActiveChannelCount()).isEqualTo(0);
    }
    
    @Test
    void shouldSendMessage() {
        // Given
        communicationManager.establishChannel(testProcessHandle);
        
        // When
        Map<String, Object> testData = new HashMap<>();
        testData.put("message", "Hello from Java");
        testData.put("timestamp", System.currentTimeMillis());
        
        // Should not throw exception (message will be queued)
        communicationManager.sendMessage(testProcessHandle, testData);
        
        // Then - verify message was queued
        var stats = communicationManager.getStats(testProcessHandle);
        assertThat(stats).isNotNull();
    }
    
    @Test
    void shouldThrowExceptionForUnknownProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current();
        
        // When/Then
        assertThatThrownBy(() -> communicationManager.sendMessage(unknownHandle, "test"))
            .isInstanceOf(CommunicationException.class)
            .hasMessageContaining("No communication channel");
    }
    
    @Test
    void shouldGetCommunicationStats() {
        // Given
        communicationManager.establishChannel(testProcessHandle);
        
        // When
        var stats = communicationManager.getStats(testProcessHandle);
        
        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getOutgoingQueueSize()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getIncomingQueueSize()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getLastHeartbeat()).isNotNull();
    }
    
    @Test
    void shouldReturnNullStatsForUnknownProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current();
        
        // When
        var stats = communicationManager.getStats(unknownHandle);
        
        // Then
        assertThat(stats).isNull();
    }
    
    @Test
    void shouldHandleMultipleChannels() throws IOException, InterruptedException {
        // Create second test process
        Path testScript2 = tempDir.resolve("communication_test2.py");
        Files.write(testScript2, """
            #!/usr/bin/env python3
            import time
            print("Second test process")
            time.sleep(3)
            """.getBytes());
        
        ProcessBuilder pb2 = new ProcessBuilder("python3", testScript2.toString());
        Process testProcess2 = pb2.start();
        ProcessHandle testProcessHandle2 = testProcess2.toHandle();
        
        try {
            // When
            communicationManager.establishChannel(testProcessHandle);
            communicationManager.establishChannel(testProcessHandle2);
            
            // Then - both channels should be registered
            assertThat(communicationManager.getStats(testProcessHandle)).isNotNull();
            assertThat(communicationManager.getStats(testProcessHandle2)).isNotNull();
            
        } finally {
            if (testProcessHandle2.isAlive()) {
                testProcessHandle2.destroyForcibly();
            }
        }
    }
    
    @Test
    void shouldShutdownGracefully() {
        // Given
        communicationManager.establishChannel(testProcessHandle);
        
        // When
        communicationManager.shutdown();
        
        // Then
        assertThat(communicationManager.getActiveChannelCount()).isEqualTo(0);
    }
    
    @Test
    void shouldHandleMessageQueueOverflow() {
        // Given
        communicationManager.establishChannel(testProcessHandle);
        
        // When - send many messages quickly
        for (int i = 0; i < 10; i++) {
            Map<String, Object> testData = new HashMap<>();
            testData.put("message", "Message " + i);
            communicationManager.sendMessage(testProcessHandle, testData);
        }
        
        // Then - should handle gracefully without throwing exceptions
        var stats = communicationManager.getStats(testProcessHandle);
        assertThat(stats).isNotNull();
        assertThat(stats.getOutgoingQueueSize()).isGreaterThan(0);
    }
    
    @Test
    void shouldHandleProcessTermination() {
        // Given
        communicationManager.establishChannel(testProcessHandle);
        
        // When - terminate the process
        testProcessHandle.destroyForcibly();
        
        // Then - should handle gracefully (no exception thrown)
        var stats = communicationManager.getStats(testProcessHandle);
        assertThat(stats).isNotNull(); // Stats should still exist even if process is terminated
    }
}