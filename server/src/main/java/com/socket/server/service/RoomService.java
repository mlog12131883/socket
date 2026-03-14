package com.socket.server.service;

import com.socket.server.domain.ChatMessage;
import com.socket.server.domain.ChatRoom;
import com.socket.server.domain.User;
import com.socket.server.repository.SessionRegistry;
import com.socket.server.serializer.JsonMessageSerializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.DataOutputStream;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 채팅방 관리 서비스 (Spring Cache 추상화 + Self-Injection 사용)
 */
@Service
@RequiredArgsConstructor
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final SessionRegistry sessionRegistry;
    private final JsonMessageSerializer<ChatMessage> serializer;
    private final ObjectProvider<RoomService> selfProvider;
    private final RedisPublisher redisPublisher;
    private final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(10);

    private RoomService getSelf() {
        return selfProvider.getIfAvailable();
    }

    @PostConstruct
    public void init() {
        // Observer 패턴: 세션 종료 이벤트 구독
        com.socket.server.event.EventBus.getInstance().subscribe(event -> {
            if (event instanceof com.socket.server.event.SessionClosedEvent) {
                handleSessionClosed((com.socket.server.event.SessionClosedEvent) event);
            }
        });
    }

    private void handleSessionClosed(com.socket.server.event.SessionClosedEvent event) {
        log.info("[RoomService] Session closed event received: {}", event.getUserId());
    }

    /**
     * 채팅방 생성 및 캐시에 저장
     */
    @CachePut(value = "rooms", key = "#roomId")
    public ChatRoom createRoom(String roomId, String roomName) {
        ChatRoom room = new ChatRoom(roomId, roomName);
        log.info("Created chat room: id={}, name={}", roomId, roomName);
        return room;
    }

    /**
     * 채팅방 정보 조회 (캐시 우선)
     */
    @Cacheable(value = "rooms", key = "#roomId")
    public Optional<ChatRoom> getRoom(String roomId) {
        log.debug("Cache miss for room: {}", roomId);
        return Optional.empty();
    }

    /**
     * 방 입장 처리
     */
    @CachePut(value = "rooms", key = "#roomId")
    public ChatRoom joinRoom(String roomId, User user) {
        return getSelf().getRoom(roomId).map(room -> {
            room.enter(user);
            log.info("User [{}] joined room [{}]", user.getId(), roomId);
            getSelf().broadcastUserList(roomId);
            return room;
        }).orElseGet(() -> {
            log.warn("Room not found for join: {}", roomId);
            return null;
        });
    }

    /**
     * 방 퇴장 처리
     */
    @CachePut(value = "rooms", key = "#roomId")
    public ChatRoom leaveRoom(String roomId, User user) {
        return getSelf().getRoom(roomId).map(room -> {
            room.leave(user);
            log.info("User [{}] left room [{}]", user.getId(), roomId);
            getSelf().broadcastUserList(roomId);
            return room;
        }).orElse(null);
    }

    /**
     * 방 존재 여부 확인
     */
    public boolean existsRoom(String roomId) {
        return getSelf().getRoom(roomId).isPresent();
    }

    /**
     * 방의 모든 사용자에게 메시지 브로드캐스트 (Redis Pub/Sub 사용)
     */
    public void broadcast(String roomId, ChatMessage message) {
        log.info("[RoomService] Publishing message to Redis channel: roomId={}, messageType={}", roomId, message.getType());
        redisPublisher.publishChat(message);
    }

    /**
     * 다른 서버 또는 현재 서버에서 발행된 Redis 메시지를 로컬 클라이언트에만 전달 (비동기 처리)
     */
    public void broadcastLocal(String roomId, ChatMessage message) {
        getSelf().getRoom(roomId).ifPresent(room -> {
            log.info("[RoomService] Local broadcasting started: roomId={}, messageType={}, localActiveUsers={}", 
                    roomId, message.getType(), room.getActiveUsers().size());
            byte[] payload = serializer.serialize(message);
            
            for (User user : room.getActiveUsers()) {
                Optional<DataOutputStream> outOpt = sessionRegistry.getOutputStream(user.getId());
                if (outOpt.isPresent()) {
                    DataOutputStream out = outOpt.get();
                    broadcastExecutor.submit(() -> {
                        try {
                            synchronized (out) {
                                out.writeInt(payload.length);
                                out.writeInt(message.getType().ordinal());
                                out.write(payload);
                                out.flush();
                            }
                        } catch (Exception e) {
                            log.error("Local broadcasting failed: userId={}, roomId={}", user.getId(), roomId, e);
                        }
                    });
                }
            }
        });
    }

    /**
     * 채팅방의 현재 참가자 목록을 모든 참여자에게 전송
     */
    public void broadcastUserList(String roomId) {
        getSelf().getRoom(roomId).ifPresent(room -> {
            String userList = room.getActiveUsers().stream()
                    .map(User::getId)
                    .collect(Collectors.joining(","));
            
            ChatMessage listMessage = new ChatMessage(
                com.socket.server.domain.MessageType.USER_LIST,
                roomId,
                "SYSTEM",
                userList
            );
            
            getSelf().broadcast(roomId, listMessage);
        });
    }
}
