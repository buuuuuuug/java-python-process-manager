package com.processmanager.exception;

/**
 * Base exception for all process manager related errors.
 */
public class ProcessManagerException extends RuntimeException {
    
    public ProcessManagerException(String message) {
        super(message);
    }
    
    public ProcessManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}