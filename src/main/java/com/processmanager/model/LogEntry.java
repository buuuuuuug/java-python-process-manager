package com.processmanager.model;

import com.processmanager.core.LogManager.LogLevel;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single log entry from a Python process.
 */
public record LogEntry(Instant timestamp, LogLevel level, String message, String source, Map<String, Object> metadata) {

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