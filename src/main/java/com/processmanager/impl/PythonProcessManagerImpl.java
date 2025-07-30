package com.processmanager.impl;

import com.processmanager.core.ProcessManager;
import com.processmanager.exception.ProcessCreationException;
import com.processmanager.exception.ProcessTerminationException;
import com.processmanager.model.LogEntry;
import com.processmanager.model.ProcessMetrics;
import com.processmanager.model.ProcessStatus;
import com.processmanager.monitoring.ProcessMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of ProcessManager for managing Python processes.
 */
public class PythonProcessManagerImpl implements ProcessManager {
    
    private static final Logger logger = LoggerFactory.getLogger(PythonProcessManagerImpl.class);
    
    private final Map<ProcessHandle, ProcessInfo> processRegistry = new ConcurrentHashMap<>();
    private final String pythonExecutable;
    private final Path bootstrapScriptPath;
    private final ProcessMetricsCollector metricsCollector;
    private final ProcessLogManagerImpl logManager;
    private final ProcessCommunicationManagerImpl communicationManager;
    
    /**
     * Process information holder.
     */
    private static class ProcessInfo {
        final Process process;
        final Instant startTime;
        final String scriptPath;
        final Map<String, String> arguments;
        volatile ProcessStatus status;
        volatile Instant lastHeartbeat;
        
        ProcessInfo(Process process, String scriptPath, Map<String, String> arguments) {
            this.process = process;
            this.startTime = Instant.now();
            this.scriptPath = scriptPath;
            this.arguments = arguments;
            this.status = ProcessStatus.STARTING;
            this.lastHeartbeat = Instant.now();
        }
    }
    
    public PythonProcessManagerImpl() {
        this("python3", "src/main/resources/pythonBootstrap.py");
    }
    
    public PythonProcessManagerImpl(String pythonExecutable, String bootstrapScriptPath) {
        this.pythonExecutable = pythonExecutable;
        this.bootstrapScriptPath = Paths.get(bootstrapScriptPath);
        this.metricsCollector = new ProcessMetricsCollector();
        this.logManager = new ProcessLogManagerImpl();
        this.communicationManager = new ProcessCommunicationManagerImpl();
        
        validatePythonExecutable();
        validateBootstrapScript();
        
        logger.info("PythonProcessManager initialized with Python: {} and bootstrap: {}", 
                   pythonExecutable, bootstrapScriptPath);
    }
    
    private void validatePythonExecutable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new ProcessCreationException(
                    "Python executable validation failed with exit code: " + exitCode);
            }
            
            logger.debug("Python executable validated successfully: {}", pythonExecutable);
            
        } catch (IOException | InterruptedException e) {
            throw new ProcessCreationException(
                "Failed to validate Python executable: " + pythonExecutable, e);
        }
    }
    
    private void validateBootstrapScript() {
        if (!Files.exists(bootstrapScriptPath)) {
            throw new ProcessCreationException(
                "Bootstrap script not found: " + bootstrapScriptPath);
        }
        
        if (!Files.isReadable(bootstrapScriptPath)) {
            throw new ProcessCreationException(
                "Bootstrap script is not readable: " + bootstrapScriptPath);
        }
        
        logger.debug("Bootstrap script validated successfully: {}", bootstrapScriptPath);
    }
    
    @Override
    public ProcessHandle createProcess(String scriptPath, Map<String, String> args) {
        logger.info("Creating Python process for script: {}", scriptPath);
        
        try {
            // Validate target script exists
            Path targetScript = Paths.get(scriptPath);
            if (!Files.exists(targetScript)) {
                throw new ProcessCreationException("Target script not found: " + scriptPath);
            }
            
            // Build command line arguments
            List<String> command = buildCommand(scriptPath, args);
            
            // Create ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(false); // Keep stdout and stderr separate
            
            // Start the process
            Process process = processBuilder.start();
            ProcessHandle handle = process.toHandle();
            
            // Register the process
            ProcessInfo processInfo = new ProcessInfo(process, scriptPath, args);
            processRegistry.put(handle, processInfo);
            
            // Start metrics collection
            metricsCollector.startMonitoring(handle);
            
            // Start log collection
            logManager.startLogCollection(handle, process);
            
            // Establish communication channel
            communicationManager.establishChannel(handle);
            
            // Update status to running if process started successfully
            if (process.isAlive()) {
                processInfo.status = ProcessStatus.RUNNING;
                logger.info("Python process created successfully with PID: {}", handle.pid());
            } else {
                processInfo.status = ProcessStatus.FAILED;
                logger.error("Python process failed to start for script: {}", scriptPath);
            }
            
            return handle;
            
        } catch (IOException e) {
            throw new ProcessCreationException("Failed to create Python process for script: " + scriptPath, e);
        }
    }
    
    private List<String> buildCommand(String scriptPath, Map<String, String> args) {
        List<String> command = new ArrayList<>();
        
        // Python executable
        command.add(pythonExecutable);
        
        // Bootstrap script
        command.add(bootstrapScriptPath.toString());
        
        // Target script
        command.add("--script");
        command.add(scriptPath);
        
        // Script arguments as JSON
        if (args != null && !args.isEmpty()) {
            command.add("--args");
            command.add(buildArgsJson(args));
        }
        
        // Default resource limits
        command.add("--memory-limit-mb");
        command.add("512");
        
        command.add("--cpu-limit-percent");
        command.add("80.0");
        
        command.add("--log-level");
        command.add("INFO");
        
        logger.debug("Built command: {}", command);
        return command;
    }
    
    private String buildArgsJson(Map<String, String> args) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, String> entry : args.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");
            json.append("\"").append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    @Override
    public ProcessStatus getProcessStatus(ProcessHandle handle) {
        ProcessInfo processInfo = processRegistry.get(handle);
        if (processInfo == null) {
            return ProcessStatus.TERMINATED;
        }
        
        // Update status based on actual process state
        if (!handle.isAlive()) {
            if (processInfo.status == ProcessStatus.RUNNING || processInfo.status == ProcessStatus.STARTING) {
                // Check exit code to determine if it completed or failed
                try {
                    int exitCode = processInfo.process.exitValue();
                    processInfo.status = (exitCode == 0) ? ProcessStatus.COMPLETED : ProcessStatus.FAILED;
                } catch (IllegalThreadStateException e) {
                    // Process hasn't terminated yet, but handle says it's not alive
                    processInfo.status = ProcessStatus.UNRESPONSIVE;
                }
            }
        } else {
            // Process is alive, check if it's responsive
            Duration timeSinceHeartbeat = Duration.between(processInfo.lastHeartbeat, Instant.now());
            if (timeSinceHeartbeat.toSeconds() > 60) { // 60 seconds timeout
                processInfo.status = ProcessStatus.UNRESPONSIVE;
            } else if (processInfo.status == ProcessStatus.STARTING) {
                // Check if enough time has passed to consider it running
                Duration timeSinceStart = Duration.between(processInfo.startTime, Instant.now());
                if (timeSinceStart.toSeconds() > 5) { // 5 seconds startup time
                    processInfo.status = ProcessStatus.RUNNING;
                }
            }
        }
        
        return processInfo.status;
    }
    
    @Override
    public boolean isProcessAlive(ProcessHandle handle) {
        return handle.isAlive();
    }
    
    @Override
    public void terminateProcess(ProcessHandle handle, Duration timeout) {
        ProcessInfo processInfo = processRegistry.get(handle);
        if (processInfo == null) {
            logger.warn("Attempted to terminate unknown process: {}", handle.pid());
            return;
        }
        
        logger.info("Terminating Python process: {}", handle.pid());
        
        try {
            // First try graceful termination
            if (handle.isAlive()) {
                boolean terminated = handle.destroy();
                if (terminated) {
                    // Wait for graceful termination
                    boolean gracefullyTerminated = processInfo.process.waitFor(
                        timeout.toMillis(), TimeUnit.MILLISECONDS);
                    
                    if (!gracefullyTerminated && handle.isAlive()) {
                        // Force termination if graceful didn't work
                        logger.warn("Graceful termination failed, forcing termination of process: {}", handle.pid());
                        handle.destroyForcibly();
                        
                        // Wait a bit more for forced termination
                        processInfo.process.waitFor(5, TimeUnit.SECONDS);
                    }
                }
            }
            
            // Stop metrics collection
            metricsCollector.stopMonitoring(handle);
            
            // Stop log collection
            logManager.stopLogCollection(handle);
            
            // Close communication channel
            communicationManager.closeChannel(handle);
            
            // Update status
            processInfo.status = ProcessStatus.TERMINATED;
            logger.info("Process terminated successfully: {}", handle.pid());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessTerminationException("Process termination interrupted for PID: " + handle.pid(), e);
        } catch (Exception e) {
            throw new ProcessTerminationException("Failed to terminate process: " + handle.pid(), e);
        }
    }
    
    @Override
    public ProcessMetrics getProcessMetrics(ProcessHandle handle) {
        ProcessInfo processInfo = processRegistry.get(handle);
        if (processInfo == null) {
            throw new IllegalArgumentException("Unknown process handle: " + handle.pid());
        }
        
        try {
            return metricsCollector.getMetrics(handle);
        } catch (IllegalArgumentException e) {
            // Fallback to basic metrics if collector doesn't have the process
            ProcessHandle.Info info = handle.info();
            
            long cpuTimeMillis = info.totalCpuDuration()
                .map(Duration::toMillis)
                .orElse(0L);
            
            Duration executionTime = Duration.between(processInfo.startTime, Instant.now());
            
            return new ProcessMetrics(
                cpuTimeMillis,
                0L, // Memory usage not available
                0L, // Peak memory usage not available
                executionTime,
                processInfo.lastHeartbeat
            );
        }
    }
    
    /**
     * Updates the heartbeat timestamp for a process.
     * This should be called when communication is received from the process.
     */
    public void updateHeartbeat(ProcessHandle handle) {
        ProcessInfo processInfo = processRegistry.get(handle);
        if (processInfo != null) {
            processInfo.lastHeartbeat = Instant.now();
        }
        // Also update metrics collector heartbeat
        metricsCollector.updateHeartbeat(handle);
    }
    
    /**
     * Gets the number of registered processes.
     */
    public int getProcessCount() {
        return processRegistry.size();
    }
    
    /**
     * Cleans up terminated processes from the registry.
     */
    public void cleanupTerminatedProcesses() {
        processRegistry.entrySet().removeIf(entry -> {
            ProcessHandle handle = entry.getKey();
            ProcessInfo info = entry.getValue();
            
            if (!handle.isAlive() && (info.status == ProcessStatus.COMPLETED || 
                                     info.status == ProcessStatus.FAILED || 
                                     info.status == ProcessStatus.TERMINATED)) {
                logger.debug("Cleaning up terminated process: {}", handle.pid());
                metricsCollector.stopMonitoring(handle);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Gets system CPU usage percentage.
     */
    public double getSystemCpuUsage() {
        return metricsCollector.getSystemCpuUsage();
    }
    
    /**
     * Gets system memory information.
     */
    public ProcessMetricsCollector.SystemMemoryInfo getSystemMemoryInfo() {
        return metricsCollector.getSystemMemoryInfo();
    }
    
    /**
     * Gets all log entries for a process.
     */
    public java.util.List<LogEntry> getProcessLogs(ProcessHandle handle) {
        return logManager.getAllLogEntries(handle);
    }
    
    /**
     * Gets log stream for a process.
     */
    public java.util.stream.Stream<LogEntry> getProcessLogStream(ProcessHandle handle) {
        return logManager.getLogStream(handle);
    }
    
    /**
     * Configures log level for a process.
     */
    public void configureProcessLogLevel(ProcessHandle handle, com.processmanager.core.LogManager.LogLevel level) {
        logManager.configureLogLevel(handle, level);
    }
    
    /**
     * Sends a message to a Python process.
     */
    public void sendMessageToProcess(ProcessHandle handle, Object data) {
        communicationManager.sendMessage(handle, data);
    }
    
    /**
     * Receives a message from a Python process.
     */
    public <T> T receiveMessageFromProcess(ProcessHandle handle, Class<T> type) {
        return communicationManager.receiveMessage(handle, type);
    }
    
    /**
     * Gets communication statistics for a process.
     */
    public ProcessCommunicationManagerImpl.CommunicationStats getCommunicationStats(ProcessHandle handle) {
        return communicationManager.getStats(handle);
    }
    
    /**
     * Shuts down the process manager and releases resources.
     */
    public void shutdown() {
        // Terminate all running processes
        processRegistry.keySet().forEach(handle -> {
            if (handle.isAlive()) {
                try {
                    terminateProcess(handle, Duration.ofSeconds(5));
                } catch (Exception e) {
                    logger.warn("Failed to terminate process {} during shutdown: {}", handle.pid(), e.getMessage());
                }
            }
        });
        
        // Shutdown metrics collector
        metricsCollector.shutdown();
        
        // Shutdown log manager
        logManager.shutdown();
        
        // Shutdown communication manager
        communicationManager.shutdown();
        
        // Clear registry
        processRegistry.clear();
        
        logger.info("PythonProcessManager shut down");
    }
}