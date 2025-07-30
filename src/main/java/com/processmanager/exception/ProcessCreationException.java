package com.processmanager.exception;

/**
 * Exception thrown when process creation fails.
 */
public class ProcessCreationException extends ProcessManagerException {
    
    public ProcessCreationException(String message) {
        super(message);
    }
    
    public ProcessCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}