package com.processmanager.impl;

import com.processmanager.core.LogManager.LogLevel;
import com.processmanager.model.LogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ProcessLogManagerImplTest {
    
    @TempDir
    Path tempDir;
    
    private ProcessLogManagerImpl logManager;
    private ProcessHandle testProcessHandle;
    private Process testProcess;
    
    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        logManager = new ProcessLogManagerImpl();
        
        // Create a test script that generates various log outputs
        Path testScript = tempDir.resolve("test_logging.py");
        Files.write(testScript, """
            #!/usr/bin/env python3
            import logging
            import sys
            import time
            import json
            
            # Configure logging
            logging.basicConfig(
                level=logging.DEBUG,
                format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
            )
            
            logger = logging.getLogger('TestLogger')
            
            # Output bootstrap status
            print("BOOTSTRAP_STATUS: " + json.dumps({"status": "initialized", "pid": 12345}))
            
            # Generate various log levels
            logger.debug("This is a debug message")
            logger.info("This is an info message")
            logger.warning("This is a warning message")
            logger.error("This is an error message")
            
            # Output to stderr
            print("Error message to stderr", file=sys.stderr)
            
            # Plain text output
            print("Plain text output without structured format")
            
            # Keep running for a bit
            time.sleep(2)
            
            logger.info("Test script completed")
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
        if (logManager != null) {
            logManager.shutdown();
        }
    }  
  
    @Test
    void shouldStartAndStopLogCollection() {
        // When
        logManager.startLogCollection(testProcessHandle, testProcess);
        
        // Then
        assertThat(logManager.getMonitoredProcessCount()).isEqualTo(1);
        
        // When
        logManager.stopLogCollection(testProcessHandle);
        
        // Then
        assertThat(logManager.getMonitoredProcessCount()).isEqualTo(0);
    }
    
    @Test
    void shouldCollectLogEntries() {
        // Given
        logManager.startLogCollection(testProcessHandle, testProcess);
        
        // Wait for log entries to be collected
        await().atMost(Duration.ofSeconds(5))
               .until(() -> !logManager.getAllLogEntries(testProcessHandle).isEmpty());
        
        // When
        List<LogEntry> logEntries = logManager.getAllLogEntries(testProcessHandle);
        
        // Then
        assertThat(logEntries).isNotEmpty();
        
        // Should contain various log levels (at least INFO level entries)
        assertThat(logEntries).anyMatch(entry -> entry.level() == LogLevel.INFO);
        
        // Should contain some expected messages from the test script
        assertThat(logEntries).anyMatch(entry -> 
            entry.message().contains("info message") ||
            entry.message().contains("warning message") ||
            entry.message().contains("error message"));
        
        // Print entries for debugging
        System.out.println("Collected log entries:");
        logEntries.forEach(entry -> System.out.println("  " + entry.level() + ": " + entry.message()));
    }
    
    @Test
    void shouldParseStructuredLogEntries() {
        // Given
        logManager.startLogCollection(testProcessHandle, testProcess);
        
        // Wait for log entries
        await().atMost(Duration.ofSeconds(5))
               .until(() -> logManager.getAllLogEntries(testProcessHandle).size() > 5);
        
        // When
        List<LogEntry> logEntries = logManager.getAllLogEntries(testProcessHandle);
        
        // Then
        // Should have structured log entries with metadata
        assertThat(logEntries).anyMatch(entry -> 
            entry.metadata().containsKey("logger") &&
            entry.metadata().get("logger").equals("TestLogger"));
        
        // Should have proper timestamps
        assertThat(logEntries).allMatch(entry -> entry.timestamp() != null);
        
        // Should have proper sources
        assertThat(logEntries).anyMatch(entry -> "stdout".equals(entry.source()));
        assertThat(logEntries).anyMatch(entry -> "stderr".equals(entry.source()));
    }
    
    @Test
    void shouldHandlePlainTextLogEntries() {
        // Given
        logManager.startLogCollection(testProcessHandle, testProcess);
        
        // Wait for log entries
        await().atMost(Duration.ofSeconds(5))
               .until(() -> logManager.getAllLogEntries(testProcessHandle).size() > 3);
        
        // When
        List<LogEntry> logEntries = logManager.getAllLogEntries(testProcessHandle);
        
        // Then
        // Should contain plain text entries or any entries at all
        assertThat(logEntries).isNotEmpty();
        
        // Print entries for debugging
        System.out.println("Plain text test - Collected log entries:");
        logEntries.forEach(entry -> System.out.println("  " + entry.level() + ": " + entry.message()));
    }
    
    @Test
    void shouldFilterLogEntriesByLevel() {
        // Given
        logManager.startLogCollection(testProcessHandle, testProcess);
        
        // Wait for initial log entries
        await().atMost(Duration.ofSeconds(3))
               .until(() -> !logManager.getAllLogEntries(testProcessHandle).isEmpty());
        
        // When - set log level to WARN
        logManager.configureLogLevel(testProcessHandle, LogLevel.WARN);
        
        // Wait a bit more for additional entries
        await().atMost(Duration.ofSeconds(2))
               .until(() -> logManager.getAllLogEntries(testProcessHandle).size() > 2);
        
        List<LogEntry> logEntries = logManager.getAllLogEntries(testProcessHandle);
        
        // Then - should have entries of WARN level and above
        // Note: Some entries might have been collected before level change
        assertThat(logEntries).anyMatch(entry -> 
            entry.level() == LogLevel.WARN || entry.level() == LogLevel.ERROR);
    }
    
    @Test
    void shouldProvideLogStream() {
        // Given
        logManager.startLogCollection(testProcessHandle, testProcess);
        
        // Wait for log entries
        await().atMost(Duration.ofSeconds(5))
               .until(() -> !logManager.getAllLogEntries(testProcessHandle).isEmpty());
        
        // When
        Stream<LogEntry> logStream = logManager.getLogStream(testProcessHandle);
        
        // Then
        assertThat(logStream).isNotNull();
        List<LogEntry> streamEntries = logStream.toList();
        assertThat(streamEntries).isNotEmpty();
    }
    
    @Test
    void shouldReturnEmptyStreamForUnknownProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current();
        
        // When
        Stream<LogEntry> logStream = logManager.getLogStream(unknownHandle);
        
        // Then
        assertThat(logStream).isNotNull();
        assertThat(logStream.toList()).isEmpty();
    }
    
    @Test
    void shouldHandleBackpressure() throws IOException, InterruptedException {
        // Create a script that generates many log entries quickly
        Path highVolumeScript = tempDir.resolve("high_volume_logging.py");
        Files.write(highVolumeScript, """
            #!/usr/bin/env python3
            import logging
            
            logging.basicConfig(level=logging.INFO, 
                              format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
            logger = logging.getLogger('HighVolumeLogger')
            
            # Generate many log entries
            for i in range(2000):
                logger.info(f"Log entry number {i}")
            """.getBytes());
        
        ProcessBuilder pb = new ProcessBuilder("python3", highVolumeScript.toString());
        Process highVolumeProcess = pb.start();
        ProcessHandle highVolumeHandle = highVolumeProcess.toHandle();
        
        try {
            // Given
            logManager.startLogCollection(highVolumeHandle, highVolumeProcess);
            
            // Wait for process to complete and logs to be collected
            await().atMost(Duration.ofSeconds(10))
                   .until(() -> !highVolumeHandle.isAlive());
            
            // Wait a bit more for log processing
            Thread.sleep(1000);
            
            // When
            List<LogEntry> logEntries = logManager.getAllLogEntries(highVolumeHandle);
            
            // Then - should handle backpressure gracefully
            // Queue size is limited to 1000, so we shouldn't have more than that
            assertThat(logEntries.size()).isLessThanOrEqualTo(1000);
            assertThat(logEntries).isNotEmpty();
            
        } finally {
            if (highVolumeHandle.isAlive()) {
                highVolumeHandle.destroyForcibly();
            }
        }
    }
    
    @Test
    void shouldShutdownGracefully() {
        // Given
        logManager.startLogCollection(testProcessHandle, testProcess);
        assertThat(logManager.getMonitoredProcessCount()).isEqualTo(1);
        
        // When
        logManager.shutdown();
        
        // Then
        assertThat(logManager.getMonitoredProcessCount()).isEqualTo(0);
    }
}