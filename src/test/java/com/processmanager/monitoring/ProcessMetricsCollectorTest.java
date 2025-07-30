package com.processmanager.monitoring;

import com.processmanager.model.ProcessMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessMetricsCollectorTest {
    
    @TempDir
    Path tempDir;
    
    private ProcessMetricsCollector metricsCollector;
    private ProcessHandle testProcessHandle;
    
    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        metricsCollector = new ProcessMetricsCollector();
        
        // Create a simple test script that runs for a while
        Path testScript = tempDir.resolve("test_metrics.py");
        Files.write(testScript, """
            #!/usr/bin/env python3
            import time
            import sys
            
            # Allocate some memory
            data = [i for i in range(10000)]
            
            # Run for a few seconds
            for i in range(10):
                time.sleep(0.5)
                print(f"Iteration {i}")
                sys.stdout.flush()
            
            print("Test script completed")
            """.getBytes());
        
        // Start a test process
        ProcessBuilder pb = new ProcessBuilder("python3", testScript.toString());
        Process process = pb.start();
        testProcessHandle = process.toHandle();
    }
    
    @AfterEach
    void tearDown() {
        if (testProcessHandle != null && testProcessHandle.isAlive()) {
            testProcessHandle.destroyForcibly();
        }
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
    }
    
    @Test
    void shouldStartAndStopMonitoring() {
        // When
        metricsCollector.startMonitoring(testProcessHandle);
        
        // Then
        assertThat(metricsCollector.getMonitoredProcessCount()).isEqualTo(1);
        
        // When
        metricsCollector.stopMonitoring(testProcessHandle);
        
        // Then
        assertThat(metricsCollector.getMonitoredProcessCount()).isEqualTo(0);
    }
    
    @Test
    void shouldCollectBasicMetrics() throws InterruptedException {
        // Given
        metricsCollector.startMonitoring(testProcessHandle);
        
        // Wait for some metrics to be collected
        Thread.sleep(2000);
        
        // When
        ProcessMetrics metrics = metricsCollector.getMetrics(testProcessHandle);
        
        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getExecutionTime()).isGreaterThan(Duration.ZERO);
        assertThat(metrics.getLastHeartbeat()).isNotNull();
        assertThat(metrics.getCpuTimeMillis()).isGreaterThanOrEqualTo(0);
        
        // Memory metrics might be 0 if OS commands are not available, but should not be negative
        assertThat(metrics.getMemoryUsageBytes()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.getPeakMemoryUsageBytes()).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    void shouldThrowExceptionForUnmonitoredProcess() {
        // Given
        ProcessHandle unknownHandle = ProcessHandle.current();
        
        // When/Then
        assertThatThrownBy(() -> metricsCollector.getMetrics(unknownHandle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Process not being monitored");
    }
    
    @Test
    void shouldUpdateHeartbeat() throws InterruptedException {
        // Given
        metricsCollector.startMonitoring(testProcessHandle);
        Thread.sleep(100);
        
        ProcessMetrics initialMetrics = metricsCollector.getMetrics(testProcessHandle);
        
        // When
        Thread.sleep(100);
        metricsCollector.updateHeartbeat(testProcessHandle);
        ProcessMetrics updatedMetrics = metricsCollector.getMetrics(testProcessHandle);
        
        // Then
        assertThat(updatedMetrics.getLastHeartbeat())
            .isAfter(initialMetrics.getLastHeartbeat());
    }
    
    @Test
    void shouldGetSystemCpuUsage() {
        // When
        double cpuUsage = metricsCollector.getSystemCpuUsage();
        
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
        ProcessMetricsCollector.SystemMemoryInfo memoryInfo = metricsCollector.getSystemMemoryInfo();
        
        // Then
        assertThat(memoryInfo).isNotNull();
        assertThat(memoryInfo.getTotalPhysicalMemory()).isGreaterThanOrEqualTo(0);
        assertThat(memoryInfo.getFreePhysicalMemory()).isGreaterThanOrEqualTo(0);
        assertThat(memoryInfo.getTotalSwapSpace()).isGreaterThanOrEqualTo(0);
        assertThat(memoryInfo.getFreeSwapSpace()).isGreaterThanOrEqualTo(0);
        
        // Used memory should be calculated correctly
        long expectedUsedMemory = memoryInfo.getTotalPhysicalMemory() - memoryInfo.getFreePhysicalMemory();
        assertThat(memoryInfo.getUsedPhysicalMemory()).isEqualTo(expectedUsedMemory);
        
        long expectedUsedSwap = memoryInfo.getTotalSwapSpace() - memoryInfo.getFreeSwapSpace();
        assertThat(memoryInfo.getUsedSwapSpace()).isEqualTo(expectedUsedSwap);
    }
    
    @Test
    void shouldHandleProcessTermination() throws InterruptedException {
        // Given
        metricsCollector.startMonitoring(testProcessHandle);
        assertThat(metricsCollector.getMonitoredProcessCount()).isEqualTo(1);
        
        // When - terminate the process
        testProcessHandle.destroyForcibly();
        Thread.sleep(1000); // Wait for termination
        
        // Then - should still be able to get metrics (though they may be stale)
        ProcessMetrics metrics = metricsCollector.getMetrics(testProcessHandle);
        assertThat(metrics).isNotNull();
    }
    
    @Test
    void shouldShutdownGracefully() {
        // Given
        metricsCollector.startMonitoring(testProcessHandle);
        assertThat(metricsCollector.getMonitoredProcessCount()).isEqualTo(1);
        
        // When
        metricsCollector.shutdown();
        
        // Then
        assertThat(metricsCollector.getMonitoredProcessCount()).isEqualTo(0);
    }
    
    @Test
    void shouldHandleMultipleProcesses() throws IOException, InterruptedException {
        // Given - create another test process
        Path testScript2 = tempDir.resolve("test_metrics2.py");
        Files.write(testScript2, """
            #!/usr/bin/env python3
            import time
            for i in range(5):
                time.sleep(1)
                print(f"Process 2 - Iteration {i}")
            """.getBytes());
        
        ProcessBuilder pb2 = new ProcessBuilder("python3", testScript2.toString());
        Process process2 = pb2.start();
        ProcessHandle handle2 = process2.toHandle();
        
        try {
            // When
            metricsCollector.startMonitoring(testProcessHandle);
            metricsCollector.startMonitoring(handle2);
            
            // Then
            assertThat(metricsCollector.getMonitoredProcessCount()).isEqualTo(2);
            
            // Both processes should have metrics
            Thread.sleep(1000);
            ProcessMetrics metrics1 = metricsCollector.getMetrics(testProcessHandle);
            ProcessMetrics metrics2 = metricsCollector.getMetrics(handle2);
            
            assertThat(metrics1).isNotNull();
            assertThat(metrics2).isNotNull();
            assertThat(metrics1.getExecutionTime()).isGreaterThan(Duration.ZERO);
            assertThat(metrics2.getExecutionTime()).isGreaterThan(Duration.ZERO);
            
        } finally {
            if (handle2.isAlive()) {
                handle2.destroyForcibly();
            }
        }
    }
}