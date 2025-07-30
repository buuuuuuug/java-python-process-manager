package com.processmanager.impl;

import com.processmanager.core.LogManager;
import com.processmanager.exception.LogCollectionException;
import com.processmanager.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Implementation of LogManager for collecting logs from Python processes.
 */
public class ProcessLogManagerImpl implements LogManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessLogManagerImpl.class);
    
    private final ExecutorService logReaderExecutor = Executors.newCachedThreadPool();
    private final Map<ProcessHandle, LogCollectionContext> logContexts = new ConcurrentHashMap<>();
    
    // Pattern for parsing structured log entries
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3}) - ([^-]+) - (\\w+) - (.*)$"
    );
    
    // Pattern for parsing bootstrap status messages
    private static final Pattern BOOTSTRAP_STATUS_PATTERN = Pattern.compile(
        "^BOOTSTRAP_STATUS: (.*)$"
    );
    
    /**
     * Context for log collection from a single process.
     */
    private static class LogCollectionContext {
        final ProcessHandle handle;
        final Process process;
        final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>(1000);
        final AtomicBoolean isCollecting = new AtomicBoolean(false);
        volatile LogLevel currentLogLevel = LogLevel.INFO;
        
        LogCollectionContext(ProcessHandle handle, Process process) {
            this.handle = handle;
            this.process = process;
        }
    }
    
    @Override
    public void startLogCollection(ProcessHandle handle) {
        throw new UnsupportedOperationException(
            "Use startLogCollection(ProcessHandle, Process) instead");
    }    
 
   @Override
    public Stream<LogEntry> getLogStream(ProcessHandle handle) {
        LogCollectionContext context = logContexts.get(handle);
        if (context == null) {
            return Stream.empty();
        }
        
        return context.logQueue.stream();
    }
    
    @Override
    public void configureLogLevel(ProcessHandle handle, LogLevel level) {
        LogCollectionContext context = logContexts.get(handle);
        if (context != null) {
            context.currentLogLevel = level;
            logger.debug("Updated log level to {} for process: {}", level, handle.pid());
        }
    }
    
    @Override
    public void stopLogCollection(ProcessHandle handle) {
        LogCollectionContext context = logContexts.remove(handle);
        if (context != null) {
            context.isCollecting.set(false);
            logger.info("Stopped log collection for process: {}", handle.pid());
        }
    }
    
    /**
     * Reads from an output stream and processes log entries.
     */
    private void readOutputStream(LogCollectionContext context, String source, 
                                 java.io.InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while (context.isCollecting.get() && (line = reader.readLine()) != null) {
                try {
                    LogEntry logEntry = parseLogLine(line, source);
                    
                    // Filter by log level
                    if (shouldIncludeLogEntry(logEntry, context.currentLogLevel)) {
                        // Add to queue with backpressure handling
                        if (!context.logQueue.offer(logEntry)) {
                            // Queue is full, remove oldest entry and add new one
                            context.logQueue.poll();
                            context.logQueue.offer(logEntry);
                            logger.warn("Log queue full for process {}, dropping oldest entry", 
                                       context.handle.pid());
                        }
                        
                        // Forward to SLF4J logger
                        forwardToSlf4j(logEntry);
                    }
                    
                } catch (Exception e) {
                    logger.warn("Failed to parse log line from process {}: {}", 
                               context.handle.pid(), e.getMessage());
                }
            }
        } catch (IOException e) {
            if (context.isCollecting.get()) {
                logger.error("Error reading {} from process {}: {}", 
                           source, context.handle.pid(), e.getMessage());
            }
        }
    }
    
    /**
     * Parses a log line into a LogEntry.
     */
    private LogEntry parseLogLine(String line, String source) {
        // Check for bootstrap status messages
        Matcher bootstrapMatcher = BOOTSTRAP_STATUS_PATTERN.matcher(line);
        if (bootstrapMatcher.matches()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("bootstrap_status", bootstrapMatcher.group(1));
            
            return new LogEntry(
                Instant.now(),
                LogLevel.INFO,
                "Bootstrap status: " + bootstrapMatcher.group(1),
                source,
                metadata
            );
        }
        
        // Try to parse structured log entry
        Matcher logMatcher = LOG_PATTERN.matcher(line);
        if (logMatcher.matches()) {
            try {
                Instant timestamp = parseTimestamp(logMatcher.group(1));
                String loggerName = logMatcher.group(2).trim();
                LogLevel level = parseLogLevel(logMatcher.group(3));
                String message = logMatcher.group(4);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("logger", loggerName);
                
                return new LogEntry(timestamp, level, message, source, metadata);
                
            } catch (Exception e) {
                logger.debug("Failed to parse structured log line: {}", line);
            }
        }
        
        // Fallback: treat as plain text log entry
        return new LogEntry(
            Instant.now(),
            LogLevel.INFO,
            line,
            source,
            new HashMap<>()
        );
    }    
   
 /**
     * Parses timestamp from log line.
     */
    private Instant parseTimestamp(String timestampStr) {
        try {
            // Handle Python logging format: 2024-01-01 12:00:00,123
            String isoFormat = timestampStr.replace(',', '.');
            return Instant.parse(isoFormat.replace(' ', 'T') + "Z");
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
    
    /**
     * Parses log level from string.
     */
    private LogLevel parseLogLevel(String levelStr) {
        try {
            return LogLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Map common Python log levels
            switch (levelStr.toUpperCase()) {
                case "WARNING": return LogLevel.WARN;
                case "CRITICAL": return LogLevel.ERROR;
                default: return LogLevel.INFO;
            }
        }
    }
    
    /**
     * Determines if a log entry should be included based on current log level.
     */
    private boolean shouldIncludeLogEntry(LogEntry entry, LogLevel currentLevel) {
        return entry.getLevel().ordinal() >= currentLevel.ordinal();
    }
    
    /**
     * Forwards log entry to SLF4J logger.
     */
    private void forwardToSlf4j(LogEntry entry) {
        Logger processLogger = LoggerFactory.getLogger("ProcessLog." + entry.getSource());
        
        String message = String.format("[%s] %s", 
                                     entry.getMetadata().getOrDefault("logger", "Unknown"), 
                                     entry.getMessage());
        
        switch (entry.getLevel()) {
            case TRACE:
                processLogger.trace(message);
                break;
            case DEBUG:
                processLogger.debug(message);
                break;
            case INFO:
                processLogger.info(message);
                break;
            case WARN:
                processLogger.warn(message);
                break;
            case ERROR:
                processLogger.error(message);
                break;
        }
    }
    

    
    /**
     * Starts log collection with explicit Process object.
     */
    public void startLogCollection(ProcessHandle handle, Process process) {
        LogCollectionContext context = new LogCollectionContext(handle, process);
        logContexts.put(handle, context);
        
        context.isCollecting.set(true);
        
        // Start stdout reader thread
        logReaderExecutor.submit(() -> readOutputStream(context, "stdout", process.getInputStream()));
        
        // Start stderr reader thread  
        logReaderExecutor.submit(() -> readOutputStream(context, "stderr", process.getErrorStream()));
        
        logger.info("Started log collection for process: {}", handle.pid());
    }
    
    /**
     * Gets all log entries for a process as a list.
     */
    public java.util.List<LogEntry> getAllLogEntries(ProcessHandle handle) {
        LogCollectionContext context = logContexts.get(handle);
        if (context == null) {
            return java.util.Collections.emptyList();
        }
        
        return new java.util.ArrayList<>(context.logQueue);
    }
    
    /**
     * Gets the number of processes being monitored.
     */
    public int getMonitoredProcessCount() {
        return logContexts.size();
    }
    
    /**
     * Shuts down the log manager.
     */
    public void shutdown() {
        // Stop all log collection
        logContexts.keySet().forEach(this::stopLogCollection);
        
        // Shutdown executor
        logReaderExecutor.shutdown();
        try {
            if (!logReaderExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                logReaderExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logReaderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ProcessLogManager shut down");
    }
}