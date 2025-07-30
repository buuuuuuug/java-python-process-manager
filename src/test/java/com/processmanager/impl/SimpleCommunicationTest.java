package com.processmanager.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCommunicationTest {
    
    private ProcessCommunicationManagerImpl communicationManager;
    
    @BeforeEach
    void setUp() {
        communicationManager = new ProcessCommunicationManagerImpl();
    }
    
    @AfterEach
    void tearDown() {
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
    void shouldInitializeWithZeroChannels() {
        // When/Then
        assertThat(communicationManager.getActiveChannelCount()).isEqualTo(0);
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
    void shouldShutdownWithoutChannels() {
        // When/Then - should not throw exception
        communicationManager.shutdown();
        assertThat(communicationManager.getActiveChannelCount()).isEqualTo(0);
    }
    
    @Test
    void shouldHandleStatsToString() {
        // Given
        var stats = new ProcessCommunicationManagerImpl.CommunicationStats(
            5, 3, java.time.Instant.now(), true);
        
        // When
        String toString = stats.toString();
        
        // Then
        assertThat(toString).contains("CommunicationStats");
        assertThat(toString).contains("outgoing=5");
        assertThat(toString).contains("incoming=3");
        assertThat(toString).contains("active=true");
    }
}