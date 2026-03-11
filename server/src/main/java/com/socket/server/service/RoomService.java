package com.socket.server.service;

import com.socket.server.domain.ChatRoom;
import com.socket.server.domain.User;
import com.socket.server.repository.CacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 채팅방 관리 서비스 (L1 캐시 활용)
 */
@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final CacheRepository<String, ChatRoom> roomCache;

    public RoomService(@Qualifier("roomCache") CacheRepository<String, ChatRoom> roomCache) {
        this.roomCache = roomCache;
    }

    /**
     * 채팅방 생성 및 캐시 저장
     */
    public ChatRoom createRoom(String roomId, String roomName) {
        ChatRoom room = new ChatRoom(roomId, roomName);
        roomCache.save(roomId, room);
        log.info("Created chat room: id={}, name={}", roomId, roomName);
        return room;
    }

    /**
     * 채팅방 정보 조회 (캐시 히트 우선)
     */
    public Optional<ChatRoom> getRoom(String roomId) {
        return roomCache.findById(roomId);
    }

    /**
     * 채팅방 입장 처리
     */
    public void joinRoom(String roomId, User user) {
        roomCache.findById(roomId).ifPresentOrElse(
                room -> {
                    room.enter(user);
                    log.info("User [{}] joined room [{}]", user.getId(), roomId);
                },
                () -> log.warn("Room not found: {}", roomId)
        );
    }

    /**
     * 채팅방 퇴장 처리
     */
    public void leaveRoom(String roomId, User user) {
        roomCache.findById(roomId).ifPresent(room -> {
            room.leave(user);
            log.info("User [{}] left room [{}]", user.getId(), roomId);
        });
    }

    /**
     * 채팅방 존재 여부 확인
     */
    public boolean existsRoom(String roomId) {
        return roomCache.existsById(roomId);
    }
}
