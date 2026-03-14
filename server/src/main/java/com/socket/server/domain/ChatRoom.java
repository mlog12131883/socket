package com.socket.server.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import com.socket.server.exception.RoomFullException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅방 도메인 클래스.
 *
 * <p>MAX_CAPACITY를 통해 수용 인원을 제한하며,
 * 분산 환경에서의 초과 입장은 {@link com.socket.server.lock.DistributedLock}으로 방지합니다.</p>
 */
@Getter
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class ChatRoom {

    /** 채팅방 최대 수용 인원 */
    public static final int MAX_CAPACITY = 100;

    private final String id;
    private final String name;
    private final Set<User> activeUsers;

    public ChatRoom(String id, String name) {
        this.id = id;
        this.name = name;
        this.activeUsers = ConcurrentHashMap.newKeySet();
    }

    /**
     * 채팅방 입장.
     * 수용 인원 초과 시 RoomFullException을 던집니다.
     */
    public void enter(User user) {
        if (user == null) return;
        if (this.activeUsers.size() >= MAX_CAPACITY) {
            throw new RoomFullException(this.id, MAX_CAPACITY);
        }
        this.activeUsers.add(user);
    }

    // 비즈니스 메서드 (퇴장)
    public void leave(User user) {
        if (user != null) {
            this.activeUsers.remove(user);
        }
    }
    
    /**
     * 외부에서 컬렉션을 직접 수정하지 못하도록 방어 복사본(또는 unmodifiable)을 반환
     */
    public Set<User> getActiveUsers() { 
        return Collections.unmodifiableSet(activeUsers); 
    }
}
