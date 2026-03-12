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
 * Chat room management service (Uses Spring Cache abstraction + Self-Injection)
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
        // Observer pattern: Subscribe to session closed event
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
     * Create chat room and store in cache
     */
    @CachePut(value = "rooms", key = "#roomId")
    public ChatRoom createRoom(String roomId, String roomName) {
        ChatRoom room = new ChatRoom(roomId, roomName);
        log.info("Created chat room: id={}, name={}", roomId, roomName);
        return room;
    }

    /**
     * Get chat room information (cache first)
     */
    @Cacheable(value = "rooms", key = "#roomId")
    public Optional<ChatRoom> getRoom(String roomId) {
        log.debug("Cache miss for room: {}", roomId);
        return Optional.empty();
    }

    /**
     * Handle room entry
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
     * Handle room exit
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
     * Check if room exists
     */
    public boolean existsRoom(String roomId) {
        return getSelf().getRoom(roomId).isPresent();
    }

    /**
     * Broadcast message to all users in the room (using Redis Pub/Sub)
     */
    public void broadcast(String roomId, ChatMessage message) {
        log.info("[RoomService] Publishing message to Redis channel: roomId={}, messageType={}", roomId, message.getType());
        redisPublisher.publishChat(message);
    }

    /**
     * Forward Redis messages issued from other servers or current server only to local clients (Asynchronous process)
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
     * Send the list of current users in the chat room to all participants
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
