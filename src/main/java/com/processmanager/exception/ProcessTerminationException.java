package com.processmanager.exception;

/**
 * Exception thrown when process termination fails.
 */
public class ProcessTerminationException extends ProcessManagerException {
    
    public ProcessTerminationException(String message) {
        super(message);
    }
    
    public ProcessTerminationException(String message, Throwable cause) {
        super(message, cause);
    }
}