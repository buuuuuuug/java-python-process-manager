package com.processmanager.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationMessageTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Test
    void shouldCreateMessageWithGeneratedId() {
        // When
        CommunicationMessage message = new CommunicationMessage("test", "payload");
        
        // Then
        assertThat(message.getMessageId()).isNotNull();
        assertThat(message.getMessageType()).isEqualTo("test");
        assertThat(message.getPayload()).isEqualTo("payload");
        assertThat(message.getTimestamp()).isNotNull();
    }
    
    @Test
    void shouldCreateMessageWithSpecificValues() {
        // Given
        String messageId = "test-id";
        String messageType = "command";
        Object payload = "test-payload";
        Instant timestamp = Instant.now();
        
        // When
        CommunicationMessage message = new CommunicationMessage(messageId, messageType, payload, timestamp);
        
        // Then
        assertThat(message.getMessageId()).isEqualTo(messageId);
        assertThat(message.getMessageType()).isEqualTo(messageType);
        assertThat(message.getPayload()).isEqualTo(payload);
        assertThat(message.getTimestamp()).isEqualTo(timestamp);
    }
    
    @Test
    void shouldSerializeAndDeserializeCorrectly() throws Exception {
        // Given
        CommunicationMessage original = new CommunicationMessage("test", "payload");
        
        // When
        String json = objectMapper.writeValueAsString(original);
        CommunicationMessage deserialized = objectMapper.readValue(json, CommunicationMessage.class);
        
        // Then
        assertThat(deserialized.getMessageId()).isEqualTo(original.getMessageId());
        assertThat(deserialized.getMessageType()).isEqualTo(original.getMessageType());
        assertThat(deserialized.getPayload()).isEqualTo(original.getPayload());
        assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
    }
}