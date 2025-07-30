package com.processmanager.core;

import com.processmanager.model.LogEntry;

import java.util.stream.Stream;

/**
 * Core interface for managing log collection from Python processes.
 * Handles stdout/stderr stream processing and log forwarding.
 */
public interface LogManager {
    
    /**
     * Starts log collection for a process.
     * 
     * @param handle Process handle
     * @throws LogCollectionException if log collection setup fails
     */
    void startLogCollection(ProcessHandle handle);
    
    /**
     * Gets a stream of log entries from a process.
     * 
     * @param handle Process handle
     * @return Stream of log entries
     */
    Stream<LogEntry> getLogStream(ProcessHandle handle);
    
    /**
     * Configures the log level for a process.
     * 
     * @param handle Process handle
     * @param level Log level to set
     */
    void configureLogLevel(ProcessHandle handle, LogLevel level);
    
    /**
     * Stops log collection for a process.
     * 
     * @param handle Process handle
     */
    void stopLogCollection(ProcessHandle handle);
    
    /**
     * Log levels supported by the system.
     */
    enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}