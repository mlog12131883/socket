package com.socket.server.service;

import com.socket.server.domain.ChatMessage;
import com.socket.server.domain.ChatRoom;
import com.socket.server.domain.User;
import com.socket.server.repository.CacheRepository;
import com.socket.server.repository.SessionRegistry;
import com.socket.server.serializer.JsonMessageSerializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 채팅방 관리 서비스 (L1 캐시 활용)
 */
@Service
@RequiredArgsConstructor
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    @Qualifier("roomCache")
    private final CacheRepository<String, ChatRoom> roomCache;
    private final SessionRegistry sessionRegistry;
    private final JsonMessageSerializer<ChatMessage> serializer;
    private final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(10); // 브로드캐스팅 전용 스레드 풀

    @PostConstruct
    public void init() {
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
                    roomCache.save(roomId, room); // 변경사항 저장 추가
                    log.info("User [{}] joined room [{}]", user.getId(), roomId);
                    broadcastUserList(roomId); // 유저 목록 브로드캐스팅 추가
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
            roomCache.save(roomId, room); // 변경사항 저장 추가
            log.info("User [{}] left room [{}]", user.getId(), roomId);
            broadcastUserList(roomId); // 유저 목록 브로드캐스팅 추가
        });
    }

    /**
     * 방에 참여 중인 모든 유저에게 메시지 브로드캐스팅 (비동기 처리)
     */
    public void broadcast(String roomId, ChatMessage message) {
        roomCache.findById(roomId).ifPresent(room -> {
            byte[] payload = serializer.serialize(message);
            
            for (User user : room.getActiveUsers()) {
                sessionRegistry.getOutputStream(user.getId()).ifPresent(out -> {
                    broadcastExecutor.submit(() -> {
                        try {
                            synchronized (out) { // 소켓 출력 스트림 동기화 (여러 스레드가 동일 소켓에 쓰는 경우 방계)
                                out.writeInt(payload.length);
                                out.writeInt(message.getType().ordinal());
                                out.write(payload);
                                out.flush();
                            }
                        } catch (Exception e) {
                            log.error("브로드캐스팅 실패: userId={}, roomId={}", user.getId(), roomId, e);
                        }
                    });
                });
            }
        });
    }

    /**
     * 채팅방 존재 여부 확인
     */
    public boolean existsRoom(String roomId) {
        return roomCache.existsById(roomId);
    }

    /**
     * 현재 채팅방의 유저 목록을 모든 참여자에게 전송
     */
    private void broadcastUserList(String roomId) {
        roomCache.findById(roomId).ifPresent(room -> {
            String userList = room.getActiveUsers().stream()
                    .map(User::getId)
                    .collect(Collectors.joining(","));
            
            ChatMessage listMessage = new ChatMessage(
                com.socket.server.domain.MessageType.USER_LIST,
                roomId,
                "SYSTEM",
                userList
            );
            
            broadcast(roomId, listMessage);
        });
    }
}
