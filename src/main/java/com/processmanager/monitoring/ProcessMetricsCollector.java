package com.processmanager.monitoring;

import com.processmanager.model.ProcessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects detailed process metrics including CPU usage, memory consumption, and execution time.
 */
public class ProcessMetricsCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessMetricsCollector.class);
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<ProcessHandle, ProcessMetricsData> metricsCache = new ConcurrentHashMap<>();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    
    // OS-specific patterns for parsing process information
    private static final Pattern MEMORY_PATTERN = Pattern.compile("(\\d+)");
    private static final boolean IS_UNIX = System.getProperty("os.name").toLowerCase().contains("nix") 
                                          || System.getProperty("os.name").toLowerCase().contains("nux")
                                          || System.getProperty("os.name").toLowerCase().contains("mac");
    
    /**
     * Internal data structure for storing process metrics.
     */
    private static class ProcessMetricsData {
        volatile long memoryUsageBytes;
        volatile long peakMemoryUsageBytes;
        volatile long cpuTimeMillis;
        volatile Instant lastUpdate;
        final Instant startTime;
        volatile Instant lastHeartbeat;
        
        ProcessMetricsData() {
            this.startTime = Instant.now();
            this.lastUpdate = Instant.now();
            this.lastHeartbeat = Instant.now();
        }
    }
    
    /**
     * Starts monitoring a process.
     */
    public void startMonitoring(ProcessHandle handle) {
        ProcessMetricsData data = new ProcessMetricsData();
        metricsCache.put(handle, data);
        
        logger.debug("Started monitoring process: {}", handle.pid());
        
        // Schedule periodic metrics collection
        scheduler.scheduleAtFixedRate(() -> collectMetrics(handle), 1, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Stops monitoring a process.
     */
    public void stopMonitoring(ProcessHandle handle) {
        metricsCache.remove(handle);
        logger.debug("Stopped monitoring process: {}", handle.pid());
    }
    
    /**
     * Gets current metrics for a process.
     */
    public ProcessMetrics getMetrics(ProcessHandle handle) {
        ProcessMetricsData data = metricsCache.get(handle);
        if (data == null) {
            throw new IllegalArgumentException("Process not being monitored: " + handle.pid());
        }
        
        // Update metrics before returning
        collectMetrics(handle);
        
        Duration executionTime = Duration.between(data.startTime, Instant.now());
        
        return new ProcessMetrics(
            data.cpuTimeMillis,
            data.memoryUsageBytes,
            data.peakMemoryUsageBytes,
            executionTime,
            data.lastHeartbeat
        );
    }
    
    /**
     * Updates the heartbeat timestamp for a process.
     */
    public void updateHeartbeat(ProcessHandle handle) {
        ProcessMetricsData data = metricsCache.get(handle);
        if (data != null) {
            data.lastHeartbeat = Instant.now();
        }
    }
    
    /**
     * Collects metrics for a specific process.
     */
    private void collectMetrics(ProcessHandle handle) {
        if (!handle.isAlive()) {
            return;
        }
        
        ProcessMetricsData data = metricsCache.get(handle);
        if (data == null) {
            return;
        }
        
        try {
            // Collect CPU time from ProcessHandle.Info
            ProcessHandle.Info info = handle.info();
            data.cpuTimeMillis = info.totalCpuDuration()
                .map(Duration::toMillis)
                .orElse(0L);
            
            // Collect memory usage using OS-specific methods
            collectMemoryUsage(handle, data);
            
            data.lastUpdate = Instant.now();
            
        } catch (Exception e) {
            logger.warn("Failed to collect metrics for process {}: {}", handle.pid(), e.getMessage());
        }
    }
    
    /**
     * Collects memory usage using OS-specific commands.
     */
    private void collectMemoryUsage(ProcessHandle handle, ProcessMetricsData data) {
        try {
            long memoryUsage = 0L;
            
            if (IS_UNIX) {
                memoryUsage = getUnixMemoryUsage(handle.pid());
            } else {
                memoryUsage = getWindowsMemoryUsage(handle.pid());
            }
            
            if (memoryUsage > 0) {
                data.memoryUsageBytes = memoryUsage;
                if (memoryUsage > data.peakMemoryUsageBytes) {
                    data.peakMemoryUsageBytes = memoryUsage;
                }
            }
            
        } catch (Exception e) {
            logger.debug("Could not collect memory usage for process {}: {}", handle.pid(), e.getMessage());
        }
    }
    
    /**
     * Gets memory usage on Unix-like systems using ps command.
     */
    private long getUnixMemoryUsage(long pid) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("ps", "-o", "rss=", "-p", String.valueOf(pid));
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                // ps returns RSS in KB, convert to bytes
                return Long.parseLong(line.trim()) * 1024;
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ps command failed with exit code: " + exitCode);
        }
        
        return 0L;
    }
    
    /**
     * Gets memory usage on Windows systems using tasklist command.
     */
    private long getWindowsMemoryUsage(long pid) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("tasklist", "/fi", "PID eq " + pid, "/fo", "csv");
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(String.valueOf(pid))) {
                    // Parse CSV format: "name","pid","session","session#","mem usage"
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        String memUsage = parts[4].replaceAll("[\"\\s,]", "");
                        if (memUsage.endsWith("K")) {
                            return Long.parseLong(memUsage.substring(0, memUsage.length() - 1)) * 1024;
                        }
                    }
                }
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("tasklist command failed with exit code: " + exitCode);
        }
        
        return 0L;
    }
    
    /**
     * Gets system-wide CPU usage.
     */
    public double getSystemCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getSystemCpuLoad() * 100.0;
        }
        return -1.0; // Not available
    }
    
    /**
     * Gets system memory information.
     */
    public SystemMemoryInfo getSystemMemoryInfo() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return new SystemMemoryInfo(
                    sunBean.getTotalMemorySize(),
                    sunBean.getFreeMemorySize(),
                sunBean.getTotalSwapSpaceSize(),
                sunBean.getFreeSwapSpaceSize()
            );
        }
        return new SystemMemoryInfo(0, 0, 0, 0);
    }
    
    /**
     * System memory information.
     */
    public static class SystemMemoryInfo {
        private final long totalPhysicalMemory;
        private final long freePhysicalMemory;
        private final long totalSwapSpace;
        private final long freeSwapSpace;
        
        public SystemMemoryInfo(long totalPhysicalMemory, long freePhysicalMemory, 
                               long totalSwapSpace, long freeSwapSpace) {
            this.totalPhysicalMemory = totalPhysicalMemory;
            this.freePhysicalMemory = freePhysicalMemory;
            this.totalSwapSpace = totalSwapSpace;
            this.freeSwapSpace = freeSwapSpace;
        }
        
        public long getTotalPhysicalMemory() { return totalPhysicalMemory; }
        public long getFreePhysicalMemory() { return freePhysicalMemory; }
        public long getUsedPhysicalMemory() { return totalPhysicalMemory - freePhysicalMemory; }
        public long getTotalSwapSpace() { return totalSwapSpace; }
        public long getFreeSwapSpace() { return freeSwapSpace; }
        public long getUsedSwapSpace() { return totalSwapSpace - freeSwapSpace; }
        
        @Override
        public String toString() {
            return String.format("SystemMemoryInfo{totalPhysical=%d, freePhysical=%d, totalSwap=%d, freeSwap=%d}",
                totalPhysicalMemory, freePhysicalMemory, totalSwapSpace, freeSwapSpace);
        }
    }
    
    /**
     * Gets the number of processes being monitored.
     */
    public int getMonitoredProcessCount() {
        return metricsCache.size();
    }
    
    /**
     * Shuts down the metrics collector.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        metricsCache.clear();
        logger.info("ProcessMetricsCollector shut down");
    }
}