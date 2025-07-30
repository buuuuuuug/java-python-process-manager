package com.processmanager.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents metrics collected from a Python process.
 */
public class ProcessMetrics {
    private final long cpuTimeMillis;
    private final long memoryUsageBytes;
    private final long peakMemoryUsageBytes;
    private final Duration executionTime;
    private final Instant lastHeartbeat;
    
    public ProcessMetrics(long cpuTimeMillis, long memoryUsageBytes, 
                         long peakMemoryUsageBytes, Duration executionTime, 
                         Instant lastHeartbeat) {
        this.cpuTimeMillis = cpuTimeMillis;
        this.memoryUsageBytes = memoryUsageBytes;
        this.peakMemoryUsageBytes = peakMemoryUsageBytes;
        this.executionTime = executionTime;
        this.lastHeartbeat = lastHeartbeat;
    }
    
    public long getCpuTimeMillis() {
        return cpuTimeMillis;
    }
    
    public long getMemoryUsageBytes() {
        return memoryUsageBytes;
    }
    
    public long getPeakMemoryUsageBytes() {
        return peakMemoryUsageBytes;
    }
    
    public Duration getExecutionTime() {
        return executionTime;
    }
    
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    @Override
    public String toString() {
        return "ProcessMetrics{" +
                "cpuTimeMillis=" + cpuTimeMillis +
                ", memoryUsageBytes=" + memoryUsageBytes +
                ", peakMemoryUsageBytes=" + peakMemoryUsageBytes +
                ", executionTime=" + executionTime +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }
}