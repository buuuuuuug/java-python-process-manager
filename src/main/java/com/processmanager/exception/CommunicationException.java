package com.processmanager.exception;

/**
 * Exception thrown when communication with Python process fails.
 */
public class CommunicationException extends ProcessManagerException {
    
    public CommunicationException(String message) {
        super(message);
    }
    
    public CommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}