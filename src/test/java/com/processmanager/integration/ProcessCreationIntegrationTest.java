package com.processmanager.integration;

import com.processmanager.impl.PythonProcessManagerImpl;
import com.processmanager.model.ProcessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for process creation and management.
 * These tests require Python to be available on the system.
 */
@EnabledIf("isPythonAvailable")
class ProcessCreationIntegrationTest {
    
    private PythonProcessManagerImpl processManager;
    
    @BeforeEach
    void setUp() {
        processManager = new PythonProcessManagerImpl();
    }
    
    @Test
    void shouldCreateAndManageRealPythonProcess() throws InterruptedException {
        // Given
        String testScript = "src/test/resources/test_script.py";
        Map<String, String> args = new HashMap<>();
        args.put("test_arg", "test_value");
        
        // When - Create process
        ProcessHandle handle = processManager.createProcess(testScript, args);
        
        // Then - Process should be created successfully
        assertThat(handle).isNotNull();
        assertThat(handle.pid()).isGreaterThan(0);
        
        // Process should be alive initially
        assertThat(processManager.isProcessAlive(handle)).isTrue();
        
        // Status should be STARTING or RUNNING
        ProcessStatus initialStatus = processManager.getProcessStatus(handle);
        assertThat(initialStatus).isIn(ProcessStatus.STARTING, ProcessStatus.RUNNING);
        
        // Wait a bit for the bootstrap to initialize
        Thread.sleep(2000);
        
        // Check final status
        ProcessStatus finalStatus = processManager.getProcessStatus(handle);
        assertThat(finalStatus).isIn(ProcessStatus.RUNNING, ProcessStatus.COMPLETED, ProcessStatus.FAILED);
        
        // Terminate the process
        processManager.terminateProcess(handle, Duration.ofSeconds(10));
        
        // Process should be terminated
        ProcessStatus terminatedStatus = processManager.getProcessStatus(handle);
        assertThat(terminatedStatus).isEqualTo(ProcessStatus.TERMINATED);
    }
    
    @Test
    void shouldHandleProcessMetricsCollection() throws InterruptedException {
        // Given
        String testScript = "src/test/resources/test_script.py";
        ProcessHandle handle = processManager.createProcess(testScript, new HashMap<>());
        
        // Wait for process to start
        Thread.sleep(1000);
        
        // When
        var metrics = processManager.getProcessMetrics(handle);
        
        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getExecutionTime()).isNotNull();
        assertThat(metrics.getLastHeartbeat()).isNotNull();
        assertThat(metrics.getCpuTimeMillis()).isGreaterThanOrEqualTo(0);
        
        // Cleanup
        processManager.terminateProcess(handle, Duration.ofSeconds(5));
    }
    
    /**
     * Check if Python is available on the system.
     */
    static boolean isPythonAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}