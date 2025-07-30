package com.processmanager.exception;

/**
 * Exception thrown when log collection fails.
 */
public class LogCollectionException extends ProcessManagerException {
    
    public LogCollectionException(String message) {
        super(message);
    }
    
    public LogCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}