package com.processmanager.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a message exchanged between Java and Python processes.
 */
public class CommunicationMessage {
    private final String messageId;
    private final String messageType;
    private final Object payload;
    private final Instant timestamp;
    
    @JsonCreator
    public CommunicationMessage(@JsonProperty("messageId") String messageId,
                               @JsonProperty("messageType") String messageType,
                               @JsonProperty("payload") Object payload,
                               @JsonProperty("timestamp") Instant timestamp) {
        this.messageId = messageId != null ? messageId : UUID.randomUUID().toString();
        this.messageType = messageType;
        this.payload = payload;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
    
    public CommunicationMessage(String messageType, Object payload) {
        this(null, messageType, payload, null);
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "CommunicationMessage{" +
                "messageId='" + messageId + '\'' +
                ", messageType='" + messageType + '\'' +
                ", payload=" + payload +
                ", timestamp=" + timestamp +
                '}';
    }
}