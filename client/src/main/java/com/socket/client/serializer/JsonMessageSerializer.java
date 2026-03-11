package com.socket.client.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonMessageSerializer<T> implements MessageSerializer<T> {

    private final ObjectMapper objectMapper;

    public JsonMessageSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public byte[] serialize(T object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("직렬화 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new RuntimeException("역직렬화 실패: " + e.getMessage(), e);
        }
    }
}
