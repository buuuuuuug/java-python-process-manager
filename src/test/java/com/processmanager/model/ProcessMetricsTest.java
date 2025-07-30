package com.processmanager.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMetricsTest {
    
    @Test
    void shouldCreateProcessMetricsWithAllFields() {
        // Given
        long cpuTime = 1000L;
        long memoryUsage = 2048L;
        long peakMemoryUsage = 4096L;
        Duration executionTime = Duration.ofSeconds(30);
        Instant heartbeat = Instant.now();
        
        // When
        ProcessMetrics metrics = new ProcessMetrics(cpuTime, memoryUsage, 
                                                   peakMemoryUsage, executionTime, heartbeat);
        
        // Then
        assertThat(metrics.getCpuTimeMillis()).isEqualTo(cpuTime);
        assertThat(metrics.getMemoryUsageBytes()).isEqualTo(memoryUsage);
        assertThat(metrics.getPeakMemoryUsageBytes()).isEqualTo(peakMemoryUsage);
        assertThat(metrics.getExecutionTime()).isEqualTo(executionTime);
        assertThat(metrics.getLastHeartbeat()).isEqualTo(heartbeat);
    }
    
    @Test
    void shouldHaveProperToStringRepresentation() {
        // Given
        ProcessMetrics metrics = new ProcessMetrics(1000L, 2048L, 4096L, 
                                                   Duration.ofSeconds(30), Instant.now());
        
        // When
        String toString = metrics.toString();
        
        // Then
        assertThat(toString).contains("ProcessMetrics{");
        assertThat(toString).contains("cpuTimeMillis=1000");
        assertThat(toString).contains("memoryUsageBytes=2048");
        assertThat(toString).contains("peakMemoryUsageBytes=4096");
    }
}