package com.processmanager.communication;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageFramingTest {
    
    @Test
    void shouldFrameAndUnframeSimpleMessage() {
        // Given
        String originalMessage = "Hello, World!";
        
        // When
        byte[] framedMessage = MessageFraming.frameMessage(originalMessage);
        String unframedMessage = MessageFraming.unframeMessage(framedMessage);
        
        // Then
        assertThat(unframedMessage).isEqualTo(originalMessage);
    }
    
    @Test
    void shouldFrameAndUnframeEmptyMessage() {
        // Given
        String originalMessage = "";
        
        // When
        byte[] framedMessage = MessageFraming.frameMessage(originalMessage);
        String unframedMessage = MessageFraming.unframeMessage(framedMessage);
        
        // Then
        assertThat(unframedMessage).isEqualTo(originalMessage);
        assertThat(framedMessage.length).isEqualTo(4); // Just the length prefix
    }
    
    @Test
    void shouldFrameAndUnframeUnicodeMessage() {
        // Given
        String originalMessage = "Hello 世界! 🌍";
        
        // When
        byte[] framedMessage = MessageFraming.frameMessage(originalMessage);
        String unframedMessage = MessageFraming.unframeMessage(framedMessage);
        
        // Then
        assertThat(unframedMessage).isEqualTo(originalMessage);
    }
    
    @Test
    void shouldFrameAndUnframeLargeMessage() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("This is line ").append(i).append("\n");
        }
        String originalMessage = sb.toString();
        
        // When
        byte[] framedMessage = MessageFraming.frameMessage(originalMessage);
        String unframedMessage = MessageFraming.unframeMessage(framedMessage);
        
        // Then
        assertThat(unframedMessage).isEqualTo(originalMessage);
    }
    
    @Test
    void shouldFrameMessageWithBytes() {
        // Given
        byte[] originalBytes = "Test message".getBytes(StandardCharsets.UTF_8);
        
        // When
        byte[] framedMessage = MessageFraming.frameMessage(originalBytes);
        byte[] unframedBytes = MessageFraming.unframeMessageBytes(framedMessage);
        
        // Then
        assertThat(unframedBytes).isEqualTo(originalBytes);
    }
    
    @Test
    void shouldExtractMessageLength() {
        // Given
        String message = "Test message";
        byte[] framedMessage = MessageFraming.frameMessage(message);
        
        // When
        byte[] lengthPrefix = new byte[4];
        System.arraycopy(framedMessage, 0, lengthPrefix, 0, 4);
        int extractedLength = MessageFraming.extractMessageLength(lengthPrefix);
        
        // Then
        int expectedLength = message.getBytes(StandardCharsets.UTF_8).length;
        assertThat(extractedLength).isEqualTo(expectedLength);
    }
    
    @Test
    void shouldValidateFramedMessage() {
        // Given
        String message = "Valid message";
        byte[] validFramedMessage = MessageFraming.frameMessage(message);
        
        // When/Then
        assertThat(MessageFraming.isValidFramedMessage(validFramedMessage)).isTrue();
    }
    
    @Test
    void shouldRejectInvalidFramedMessage() {
        // Given
        byte[] invalidMessage1 = new byte[]{1, 2, 3}; // Too short
        byte[] invalidMessage2 = new byte[]{0, 0, 0, 10, 1, 2, 3}; // Length mismatch
        
        // When/Then
        assertThat(MessageFraming.isValidFramedMessage(invalidMessage1)).isFalse();
        assertThat(MessageFraming.isValidFramedMessage(invalidMessage2)).isFalse();
    }
    
    @Test
    void shouldThrowExceptionForInvalidLengthPrefix() {
        // Given
        byte[] invalidLengthPrefix = new byte[]{1, 2, 3}; // Wrong size
        
        // When/Then
        assertThatThrownBy(() -> MessageFraming.extractMessageLength(invalidLengthPrefix))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Length prefix must be exactly 4 bytes");
    }
    
    @Test
    void shouldThrowExceptionForTooShortFramedMessage() {
        // Given
        byte[] tooShortMessage = new byte[]{1, 2}; // Less than 4 bytes
        
        // When/Then
        assertThatThrownBy(() -> MessageFraming.unframeMessage(tooShortMessage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Framed message too short");
    }
    
    @Test
    void shouldThrowExceptionForInvalidMessageLength() {
        // Given - Create a message with invalid length prefix
        byte[] invalidMessage = new byte[]{0, 0, 0, -1, 1, 2, 3, 4}; // Negative length
        
        // When/Then
        assertThatThrownBy(() -> MessageFraming.unframeMessage(invalidMessage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid message length");
    }
    
    @Test
    void shouldGetCorrectLengthPrefixSize() {
        // When/Then
        assertThat(MessageFraming.getLengthPrefixSize()).isEqualTo(4);
    }
    
    @Test
    void shouldHandleMessageWithSpecialCharacters() {
        // Given
        String originalMessage = "Message with\nnewlines\tand\rtabs\"quotes\"";
        
        // When
        byte[] framedMessage = MessageFraming.frameMessage(originalMessage);
        String unframedMessage = MessageFraming.unframeMessage(framedMessage);
        
        // Then
        assertThat(unframedMessage).isEqualTo(originalMessage);
    }
}