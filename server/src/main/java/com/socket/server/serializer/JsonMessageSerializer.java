package com.socket.server.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Jackson ObjectMapper를 활용한 JSON 메시지 직렬화 구현체
 */
@Component
public class JsonMessageSerializer<T> implements MessageSerializer<T> {

    private final ObjectMapper objectMapper;

    public JsonMessageSerializer() {
        this.objectMapper = new ObjectMapper();
        // Java 8 LocalDateTime 지원 모듈 등록
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
    public <R> R deserialize(byte[] bytes, Class<R> clazz) {
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new RuntimeException("역직렬화 실패: " + e.getMessage(), e);
        }
    }
}
