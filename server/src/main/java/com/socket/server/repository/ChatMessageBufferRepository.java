package com.socket.server.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socket.server.domain.ChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ChatMessageBufferRepository {

    private static final String BUFFER_KEY = "buffer:chat:messages";

    private static final DefaultRedisScript<List> POP_BATCH_SCRIPT = new DefaultRedisScript<>(
            """
            local key = KEYS[1]
            local n = tonumber(ARGV[1])
            local len = redis.call('LLEN', key)
            if len == 0 then
              return {}
            end
            local endIndex = n - 1
            if endIndex > len - 1 then
              endIndex = len - 1
            end
            local items = redis.call('LRANGE', key, 0, endIndex)
            redis.call('LTRIM', key, endIndex + 1, -1)
            return items
            """,
            List.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ChatMessageBufferRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void push(ChatMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForList().leftPush(BUFFER_KEY, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ChatMessage", e);
        }
    }

    public List<ChatMessage> popBatch(int batchSize) {
        List<String> raw = (List<String>) stringRedisTemplate.execute(
                POP_BATCH_SCRIPT,
                Collections.singletonList(BUFFER_KEY),
                String.valueOf(batchSize)
        );

        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        return raw.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, ChatMessage.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }
}

