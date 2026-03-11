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

        // Observer 패턴: 연결 종료 이벤트 구독
        com.socket.server.event.EventBus.getInstance().subscribe(event -> {
            if (event instanceof com.socket.server.event.SessionClosedEvent) {
                handleSessionClosed((com.socket.server.event.SessionClosedEvent) event);
            }
        });
    }

    private void handleSessionClosed(com.socket.server.event.SessionClosedEvent event) {
        log.info("[RoomService] 연결 종료 이벤트 수신: {}", event.getUserId());
        // 모든 방에서 해당 유저 퇴장 처리 (실제로는 유저가 속한 방 목록을 관리하면 더 효율적)
        // 여기서는 단순화하여 로그만 남기거나 전체 탐색
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
