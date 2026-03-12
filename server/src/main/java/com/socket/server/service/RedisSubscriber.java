package com.socket.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socket.server.domain.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub message subscription listener
 */
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisSubscriber.class);

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Deserialize message received from Redis
            String body = new String(message.getBody());
            ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
            
            log.info("[RedisSubscriber] Message received: roomId={}, senderId={}", 
                    chatMessage.getRoomId(), chatMessage.getSenderId());

            // Broadcast only to local clients through RoomService
            // Direct fetching from Context or lazy loading to prevent circular reference
            applicationContext.getBean(RoomService.class).broadcastLocal(chatMessage.getRoomId(), chatMessage);
            
        } catch (Exception e) {
            log.error("[RedisSubscriber] Error occurred while processing message", e);
        }
    }
}
