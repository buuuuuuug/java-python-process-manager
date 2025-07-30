package com.processmanager.core;

import com.processmanager.model.ProcessMetrics;
import com.processmanager.model.ProcessStatus;

import java.time.Duration;
import java.util.Map;

/**
 * Core interface for managing Python processes.
 * Handles process creation, lifecycle management, and monitoring.
 */
public interface ProcessManager {
    
    /**
     * Creates a new Python process with the specified script and arguments.
     * 
     * @param scriptPath Path to the Python script to execute
     * @param args Arguments to pass to the script
     * @return ProcessHandle for the created process
     * @throws ProcessCreationException if process creation fails
     */
    ProcessHandle createProcess(String scriptPath, Map<String, String> args);
    
    /**
     * Gets the current status of a process.
     * 
     * @param handle Process handle
     * @return Current process status
     */
    ProcessStatus getProcessStatus(ProcessHandle handle);
    
    /**
     * Checks if a process is currently alive.
     * 
     * @param handle Process handle
     * @return true if process is alive, false otherwise
     */
    boolean isProcessAlive(ProcessHandle handle);
    
    /**
     * Terminates a process gracefully with optional timeout.
     * 
     * @param handle Process handle
     * @param timeout Maximum time to wait for graceful termination
     * @throws ProcessTerminationException if termination fails
     */
    void terminateProcess(ProcessHandle handle, Duration timeout);
    
    /**
     * Gets current metrics for a process.
     * 
     * @param handle Process handle
     * @return Process metrics
     */
    ProcessMetrics getProcessMetrics(ProcessHandle handle);
}