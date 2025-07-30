package com.processmanager.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.processmanager.communication.CommunicationChannel;
import com.processmanager.communication.SocketChannel;
import com.processmanager.core.CommunicationManager;
import com.processmanager.exception.CommunicationException;
import com.processmanager.model.CommunicationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of CommunicationManager for bidirectional communication with Python processes.
 */
public class ProcessCommunicationManagerImpl implements CommunicationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessCommunicationManagerImpl.class);
    
    private final ObjectMapper objectMapper;
    private final ExecutorService messageExecutor;
    private final ScheduledExecutorService retryExecutor;
    private final ConcurrentHashMap<ProcessHandle, CommunicationContext> communicationContexts;
    
    // Configuration
    private final Duration defaultTimeout = Duration.ofSeconds(30);
    private final int maxRetryAttempts = 3;
    private final Duration initialRetryDelay = Duration.ofMillis(500);
    private final double retryBackoffMultiplier = 2.0;
    
    /**
     * Context for managing communication with a single process.
     */
    private static class CommunicationContext {
        final ProcessHandle processHandle;
        final CommunicationChannel channel;
        final BlockingQueue<CommunicationMessage> outgoingQueue;
        final BlockingQueue<CommunicationMessage> incomingQueue;
        final AtomicBoolean isActive;
        final AtomicInteger messageIdCounter;
        volatile Instant lastHeartbeat;
        
        CommunicationContext(ProcessHandle processHandle, CommunicationChannel channel) {
            this.processHandle = processHandle;
            this.channel = channel;
            this.outgoingQueue = new LinkedBlockingQueue<>(1000);
            this.incomingQueue = new LinkedBlockingQueue<>(1000);
            this.isActive = new AtomicBoolean(false);
            this.messageIdCounter = new AtomicInteger(0);
            this.lastHeartbeat = Instant.now();
        }
    }
    
    public ProcessCommunicationManagerImpl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.messageExecutor = Executors.newCachedThreadPool();
        this.retryExecutor = Executors.newScheduledThreadPool(2);
        this.communicationContexts = new ConcurrentHashMap<>();
        
        logger.info("ProcessCommunicationManager initialized");
    }
    
    @Override
    public void establishChannel(ProcessHandle handle) {
        logger.info("Establishing communication channel for process: {}", handle.pid());
        
        try {
            // Create socket channel (can be configured to use named pipes instead)
            int port = findAvailablePort();
            CommunicationChannel channel = new SocketChannel("process-" + handle.pid(), port);
            
            // Create communication context
            CommunicationContext context = new CommunicationContext(handle, channel);
            communicationContexts.put(handle, context);
            
            // Start channel in background
            CompletableFuture.runAsync(() -> {
                try {
                    channel.open();
                    context.isActive.set(true);
                    
                    // Start message processing threads
                    startMessageProcessing(context);
                    
                    logger.info("Communication channel established for process: {}", handle.pid());
                    
                } catch (IOException e) {
                    logger.error("Failed to open communication channel for process {}: {}", 
                               handle.pid(), e.getMessage());
                    context.isActive.set(false);
                }
            }, messageExecutor);
            
        } catch (Exception e) {
            throw new CommunicationException("Failed to establish channel for process: " + handle.pid(), e);
        }
    }
    
    @Override
    public void sendMessage(ProcessHandle handle, Object data) {
        CommunicationContext context = communicationContexts.get(handle);
        if (context == null) {
            throw new CommunicationException("No communication channel for process: " + handle.pid());
        }
        
        try {
            // Create message
            String messageId = generateMessageId(context);
            CommunicationMessage message = new CommunicationMessage(messageId, "data", data, null);
            
            // Add to outgoing queue
            if (!context.outgoingQueue.offer(message)) {
                throw new CommunicationException("Outgoing message queue full for process: " + handle.pid());
            }
            
            logger.debug("Message queued for process {}: {}", handle.pid(), messageId);
            
        } catch (Exception e) {
            throw new CommunicationException("Failed to send message to process: " + handle.pid(), e);
        }
    }
    
    @Override
    public <T> T receiveMessage(ProcessHandle handle, Class<T> type) {
        CommunicationContext context = communicationContexts.get(handle);
        if (context == null) {
            throw new CommunicationException("No communication channel for process: " + handle.pid());
        }
        
        try {
            // Wait for message with timeout
            CommunicationMessage message = context.incomingQueue.poll(
                defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
            
            if (message == null) {
                throw new CommunicationException("Timeout waiting for message from process: " + handle.pid());
            }
            
            // Deserialize payload
            return objectMapper.convertValue(message.getPayload(), type);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommunicationException("Interrupted while waiting for message", e);
        } catch (Exception e) {
            throw new CommunicationException("Failed to receive message from process: " + handle.pid(), e);
        }
    }
    
    @Override
    public void closeChannel(ProcessHandle handle) {
        CommunicationContext context = communicationContexts.remove(handle);
        if (context != null) {
            context.isActive.set(false);
            
            try {
                context.channel.close();
                logger.info("Communication channel closed for process: {}", handle.pid());
            } catch (IOException e) {
                logger.warn("Error closing channel for process {}: {}", handle.pid(), e.getMessage());
            }
        }
    }
    
    /**
     * Starts message processing threads for a communication context.
     */
    private void startMessageProcessing(CommunicationContext context) {
        // Start outgoing message processor
        messageExecutor.submit(() -> processOutgoingMessages(context));
        
        // Start incoming message processor
        messageExecutor.submit(() -> processIncomingMessages(context));
        
        // Start heartbeat sender
        retryExecutor.scheduleAtFixedRate(() -> sendHeartbeat(context), 10, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Processes outgoing messages from the queue.
     */
    private void processOutgoingMessages(CommunicationContext context) {
        while (context.isActive.get()) {
            try {
                CommunicationMessage message = context.outgoingQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    sendMessageWithRetry(context, message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing outgoing message for process {}: {}", 
                           context.processHandle.pid(), e.getMessage());
            }
        }
    }
    
    /**
     * Processes incoming messages from the channel.
     */
    private void processIncomingMessages(CommunicationContext context) {
        while (context.isActive.get()) {
            try {
                String messageJson = context.channel.receiveMessage();
                CommunicationMessage message = objectMapper.readValue(messageJson, CommunicationMessage.class);
                
                // Handle different message types
                if ("heartbeat".equals(message.getMessageType())) {
                    context.lastHeartbeat = Instant.now();
                    logger.debug("Heartbeat received from process: {}", context.processHandle.pid());
                } else {
                    // Add to incoming queue
                    if (!context.incomingQueue.offer(message)) {
                        logger.warn("Incoming message queue full for process: {}", context.processHandle.pid());
                        // Remove oldest message and add new one
                        context.incomingQueue.poll();
                        context.incomingQueue.offer(message);
                    }
                }
                
            } catch (IOException e) {
                if (context.isActive.get()) {
                    logger.error("Error receiving message from process {}: {}", 
                               context.processHandle.pid(), e.getMessage());
                }
                break;
            } catch (Exception e) {
                logger.error("Error processing incoming message from process {}: {}", 
                           context.processHandle.pid(), e.getMessage());
            }
        }
    }
    
    /**
     * Sends a message with retry logic and exponential backoff.
     */
    private void sendMessageWithRetry(CommunicationContext context, CommunicationMessage message) {
        sendMessageWithRetry(context, message, 0);
    }
    
    private void sendMessageWithRetry(CommunicationContext context, CommunicationMessage message, int attempt) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            context.channel.sendMessage(messageJson);
            
            logger.debug("Message sent to process {}: {}", context.processHandle.pid(), message.getMessageId());
            
        } catch (Exception e) {
            if (attempt < maxRetryAttempts) {
                long delayMs = (long) (initialRetryDelay.toMillis() * Math.pow(retryBackoffMultiplier, attempt));
                
                logger.warn("Failed to send message to process {} (attempt {}), retrying in {}ms: {}", 
                           context.processHandle.pid(), attempt + 1, delayMs, e.getMessage());
                
                retryExecutor.schedule(() -> sendMessageWithRetry(context, message, attempt + 1), 
                                     delayMs, TimeUnit.MILLISECONDS);
            } else {
                logger.error("Failed to send message to process {} after {} attempts: {}", 
                           context.processHandle.pid(), maxRetryAttempts, e.getMessage());
            }
        }
    }
    
    /**
     * Sends a heartbeat message to maintain connection health.
     */
    private void sendHeartbeat(CommunicationContext context) {
        if (!context.isActive.get()) {
            return;
        }
        
        try {
            String messageId = generateMessageId(context);
            CommunicationMessage heartbeat = new CommunicationMessage(messageId, "heartbeat", "ping", null);
            
            String messageJson = objectMapper.writeValueAsString(heartbeat);
            context.channel.sendMessage(messageJson);
            
            logger.debug("Heartbeat sent to process: {}", context.processHandle.pid());
            
        } catch (Exception e) {
            logger.warn("Failed to send heartbeat to process {}: {}", 
                       context.processHandle.pid(), e.getMessage());
        }
    }
    
    /**
     * Generates a unique message ID for a context.
     */
    private String generateMessageId(CommunicationContext context) {
        return "msg-" + context.processHandle.pid() + "-" + context.messageIdCounter.incrementAndGet();
    }
    
    /**
     * Finds an available port for socket communication.
     */
    private int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            // Fallback to a range of ports
            return 20000 + (int) (Math.random() * 10000);
        }
    }
    
    /**
     * Gets the number of active communication channels.
     */
    public int getActiveChannelCount() {
        return (int) communicationContexts.values().stream()
            .filter(context -> context.isActive.get())
            .count();
    }
    
    /**
     * Gets communication statistics for a process.
     */
    public CommunicationStats getStats(ProcessHandle handle) {
        CommunicationContext context = communicationContexts.get(handle);
        if (context == null) {
            return null;
        }
        
        return new CommunicationStats(
            context.outgoingQueue.size(),
            context.incomingQueue.size(),
            context.lastHeartbeat,
            context.isActive.get()
        );
    }
    
    /**
     * Communication statistics for a process.
     */
    public static class CommunicationStats {
        private final int outgoingQueueSize;
        private final int incomingQueueSize;
        private final Instant lastHeartbeat;
        private final boolean isActive;
        
        public CommunicationStats(int outgoingQueueSize, int incomingQueueSize, 
                                 Instant lastHeartbeat, boolean isActive) {
            this.outgoingQueueSize = outgoingQueueSize;
            this.incomingQueueSize = incomingQueueSize;
            this.lastHeartbeat = lastHeartbeat;
            this.isActive = isActive;
        }
        
        public int getOutgoingQueueSize() { return outgoingQueueSize; }
        public int getIncomingQueueSize() { return incomingQueueSize; }
        public Instant getLastHeartbeat() { return lastHeartbeat; }
        public boolean isActive() { return isActive; }
        
        @Override
        public String toString() {
            return String.format("CommunicationStats{outgoing=%d, incoming=%d, lastHeartbeat=%s, active=%s}",
                outgoingQueueSize, incomingQueueSize, lastHeartbeat, isActive);
        }
    }
    
    /**
     * Shuts down the communication manager.
     */
    public void shutdown() {
        // Close all channels
        communicationContexts.keySet().forEach(this::closeChannel);
        
        // Shutdown executors
        messageExecutor.shutdown();
        retryExecutor.shutdown();
        
        try {
            if (!messageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                messageExecutor.shutdownNow();
            }
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            messageExecutor.shutdownNow();
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ProcessCommunicationManager shut down");
    }
}