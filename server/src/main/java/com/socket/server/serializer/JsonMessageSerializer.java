package com.socket.server.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JSON message serializer implementation using Jackson ObjectMapper
 */
@Component
public class JsonMessageSerializer<T> implements MessageSerializer<T> {

    private final ObjectMapper objectMapper;

    public JsonMessageSerializer() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 LocalDateTime support module
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public byte[] serialize(T object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Serialization failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <R> R deserialize(byte[] bytes, Class<R> clazz) {
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Deserialization failed: " + e.getMessage(), e);
        }
    }
}
