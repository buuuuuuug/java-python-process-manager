package com.processmanager.model;

import com.processmanager.core.LogManager.LogLevel;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single log entry from a Python process.
 */
public class LogEntry {
    private final Instant timestamp;
    private final LogLevel level;
    private final String message;
    private final String source;
    private final Map<String, Object> metadata;
    
    public LogEntry(Instant timestamp, LogLevel level, String message, 
                   String source, Map<String, Object> metadata) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.source = source;
        this.metadata = metadata;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public LogLevel getLevel() {
        return level;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getSource() {
        return source;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    @Override
    public String toString() {
        return "LogEntry{" +
                "timestamp=" + timestamp +
                ", level=" + level +
                ", message='" + message + '\'' +
                ", source='" + source + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}