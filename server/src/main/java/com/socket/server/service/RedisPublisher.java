package com.socket.server.service;

import com.socket.server.domain.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 메시지 발행 서비스
 */
@Service
@RequiredArgsConstructor
public class RedisPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(String topic, ChatMessage message) {
        log.debug("Publishing message to topic [{}]: {}", topic, message);
        redisTemplate.convertAndSend(topic, message);
    }

    /**
     * 공통 채팅 채널로 메시지 발행
     */
    public void publishChat(ChatMessage message) {
        publish("chat:rooms", message);
    }
}
