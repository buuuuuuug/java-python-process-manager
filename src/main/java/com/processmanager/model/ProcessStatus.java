package com.processmanager.model;

/**
 * Represents the current status of a Python process.
 */
public enum ProcessStatus {
    /**
     * Process is starting up
     */
    STARTING,
    
    /**
     * Process is running normally
     */
    RUNNING,
    
    /**
     * Process completed successfully
     */
    COMPLETED,
    
    /**
     * Process failed with an error
     */
    FAILED,
    
    /**
     * Process was terminated
     */
    TERMINATED,
    
    /**
     * Process is not responding
     */
    UNRESPONSIVE
}