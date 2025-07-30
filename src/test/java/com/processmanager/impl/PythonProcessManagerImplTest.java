package com.processmanager.impl;

import com.processmanager.exception.ProcessCreationException;
import com.processmanager.exception.ProcessTerminationException;
import com.processmanager.model.ProcessMetrics;
import com.processmanager.model.ProcessStatus;
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

class PythonProcessManagerImplTest {
    
    @TempDir
    Path tempDir;
    
    private PythonProcessManagerImpl processManager;
    private Path testScript;
    private Path bootstrapScript;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create a test bootstrap script
        bootstrapScript = tempDir.resolve("testBootstrap.py");
        Files.write(bootstrapScript, """
            #!/usr/bin/env python3
            import sys
            import time
            import json
            
            print("BOOTSTRAP_STATUS: " + json.dumps({
                "status": "initialized",
                "pid": 12345
            }))
            
            # Simple test script that runs for a short time
            time.sleep(0.1)
            print("Test bootstrap completed")
            """.getBytes());
        
        // Create a test target script
        testScript = tempDir.resolve("test_target.py");
        Files.write(testScript, """
            #!/usr/bin/env python3
            import sys
            import json
            
            print("Test target script started")
            print("Arguments:", sys.argv)
            
            # Output test data
            test_data = {"message": "success", "status": "completed"}
            print("OUTPUT:", json.dumps(test_data))
            """.getBytes());
        
        // Initialize process manager with test bootstrap
        processManager = new PythonProcessManagerImpl("python3", bootstrapScript.toString());
    }
    
    @Test
    void shouldCreateProcessSuccessfully() {
        // Given
        Map<String, String> args = new HashMap<>();
        args.put("test", "value");
        
        // When
        ProcessHandle handle = processManager.createProcess(testScript.toString(), args);
        
        // Then
        assertThat(handle).isNotNull();
        assertThat(handle.pid()).isGreaterThan(0);
        assertThat(processManager.getProcessCount()).isEqualTo(1);
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    @Test
    void shouldThrowExceptionForNonExistentScript() {
        // Given
        String nonExistentScript = "/path/to/nonexistent/script.py";
        Map<String, String> args = new HashMap<>();
        
        // When/Then
        assertThatThrownBy(() -> processManager.createProcess(nonExistentScript, args))
            .isInstanceOf(ProcessCreationException.class)
            .hasMessageContaining("Target script not found");
    }
    
    @Test
    void shouldCreateProcessWithEmptyArguments() {
        // When
        ProcessHandle handle = processManager.createProcess(testScript.toString(), null);
        
        // Then
        assertThat(handle).isNotNull();
        assertThat(processManager.isProcessAlive(handle)).isTrue();
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    @Test
    void shouldGetProcessStatusCorrectly() throws InterruptedException {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        
        // When - Initially should be STARTING or RUNNING
        ProcessStatus initialStatus = processManager.getProcessStatus(handle);
        
        // Then
        assertThat(initialStatus).isIn(ProcessStatus.STARTING, ProcessStatus.RUNNING);
        
        // Wait for process to complete
        Thread.sleep(1000);
        
        // Status should eventually be COMPLETED or FAILED
        ProcessStatus finalStatus = processManager.getProcessStatus(handle);
        assertThat(finalStatus).isIn(ProcessStatus.COMPLETED, ProcessStatus.FAILED, ProcessStatus.TERMINATED);
    }
    
    @Test
    void shouldReturnTerminatedStatusForUnknownProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current(); // Use current process as unknown
        
        // When
        ProcessStatus status = processManager.getProcessStatus(unknownHandle);
        
        // Then
        assertThat(status).isEqualTo(ProcessStatus.TERMINATED);
    }
    
    @Test
    void shouldCheckIfProcessIsAlive() {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        
        // When/Then - Process should be alive initially
        assertThat(processManager.isProcessAlive(handle)).isTrue();
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    @Test
    void shouldTerminateProcessGracefully() {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        assertThat(processManager.isProcessAlive(handle)).isTrue();
        
        // When
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
        
        // Then - Process should be terminated
        ProcessStatus status = processManager.getProcessStatus(handle);
        assertThat(status).isEqualTo(ProcessStatus.TERMINATED);
    }
    
    @Test
    void shouldHandleTerminationOfNonExistentProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current();
        
        // When/Then - Should not throw exception
        processManager.terminateProcess(unknownHandle, Duration.ofSeconds(1));
    }
    
    @Test
    void shouldGetProcessMetrics() throws InterruptedException {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        
        // Wait a bit for metrics to be collected
        Thread.sleep(1000);
        
        // When
        ProcessMetrics metrics = processManager.getProcessMetrics(handle);
        
        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getCpuTimeMillis()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.getExecutionTime()).isNotNull();
        assertThat(metrics.getLastHeartbeat()).isNotNull();
        assertThat(metrics.getMemoryUsageBytes()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.getPeakMemoryUsageBytes()).isGreaterThanOrEqualTo(0);
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    @Test
    void shouldThrowExceptionForMetricsOfUnknownProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current();
        
        // When/Then
        assertThatThrownBy(() -> processManager.getProcessMetrics(unknownHandle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown process handle");
    }
    
    @Test
    void shouldUpdateHeartbeat() {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        ProcessMetrics initialMetrics = processManager.getProcessMetrics(handle);
        
        // When
        try {
            Thread.sleep(100); // Small delay
            processManager.updateHeartbeat(handle);
            ProcessMetrics updatedMetrics = processManager.getProcessMetrics(handle);
            
            // Then
            assertThat(updatedMetrics.getLastHeartbeat())
                .isAfter(initialMetrics.getLastHeartbeat());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    @Test
    void shouldCleanupTerminatedProcesses() throws InterruptedException {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        assertThat(processManager.getProcessCount()).isEqualTo(1);
        
        // Explicitly terminate the process
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
        
        // Wait a bit to ensure termination is complete
        Thread.sleep(500);
        
        // When
        processManager.cleanupTerminatedProcesses();
        
        // Then
        assertThat(processManager.getProcessCount()).isEqualTo(0);
    }
    
    @Test
    void shouldBuildJsonArgsCorrectly() {
        // Given
        Map<String, String> args = new HashMap<>();
        args.put("key1", "value1");
        args.put("key2", "value with \"quotes\" and \\backslashes");
        args.put("key3", "value\nwith\nnewlines");
        
        // When
        ProcessHandle handle = processManager.createProcess(testScript.toString(), args);
        
        // Then - Process should start successfully despite complex arguments
        assertThat(handle).isNotNull();
        assertThat(processManager.isProcessAlive(handle)).isTrue();
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    @Test
    void shouldThrowExceptionForInvalidPythonExecutable() {
        // When/Then
        assertThatThrownBy(() -> new PythonProcessManagerImpl("invalid-python", bootstrapScript.toString()))
            .isInstanceOf(ProcessCreationException.class)
            .hasMessageContaining("Failed to validate Python executable");
    }
    
    @Test
    void shouldThrowExceptionForNonExistentBootstrapScript() {
        // When/Then
        assertThatThrownBy(() -> new PythonProcessManagerImpl("python3", "/nonexistent/bootstrap.py"))
            .isInstanceOf(ProcessCreationException.class)
            .hasMessageContaining("Bootstrap script not found");
    }
    
    @Test
    void shouldGetSystemCpuUsage() {
        // When
        double cpuUsage = processManager.getSystemCpuUsage();
        
        // Then
        // CPU usage should be between 0 and 100, or -1 if not available
        assertThat(cpuUsage).satisfiesAnyOf(
            usage -> assertThat(usage).isBetween(0.0, 100.0),
            usage -> assertThat(usage).isEqualTo(-1.0)
        );
    }
    
    @Test
    void shouldGetSystemMemoryInfo() {
        // When
        var memoryInfo = processManager.getSystemMemoryInfo();
        
        // Then
        assertThat(memoryInfo).isNotNull();
        assertThat(memoryInfo.getTotalPhysicalMemory()).isGreaterThanOrEqualTo(0);
        assertThat(memoryInfo.getFreePhysicalMemory()).isGreaterThanOrEqualTo(0);
        assertThat(memoryInfo.getUsedPhysicalMemory()).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    void shouldShutdownGracefully() {
        // Given
        ProcessHandle handle = processManager.createProcess(testScript.toString(), new HashMap<>());
        assertThat(processManager.getProcessCount()).isEqualTo(1);
        
        // When
        processManager.shutdown();
        
        // Then
        assertThat(processManager.getProcessCount()).isEqualTo(0);
    }
}