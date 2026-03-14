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
 * Redis Pub/Sub 메시지 구독 리스너
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
            // Redis로부터 수신된 메시지 역직렬화
            String body = new String(message.getBody());
            ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
            
            log.info("[RedisSubscriber] Message received: roomId={}, senderId={}", 
                    chatMessage.getRoomId(), chatMessage.getSenderId());

            // RoomService를 통해 로컬 클라이언트에만 브로드캐스트
            // 순환 참조 방지를 위해 Context에서 직접 조회 또는 지연 로딩
            applicationContext.getBean(RoomService.class).broadcastLocal(chatMessage.getRoomId(), chatMessage);
            
        } catch (Exception e) {
            log.error("[RedisSubscriber] Error occurred while processing message", e);
        }
    }
}
