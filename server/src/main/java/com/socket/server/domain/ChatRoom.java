package com.socket.server.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅방 도메인 클래스
 */
@Getter
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class ChatRoom {
    private final String id;
    private final String name;
    private final Set<User> activeUsers;

    public ChatRoom(String id, String name) {
        this.id = id;
        this.name = name;
        this.activeUsers = ConcurrentHashMap.newKeySet();
    }

    // 비즈니스 메서드 (입장)
    public void enter(User user) {
        if (user != null) {
            this.activeUsers.add(user);
        }
    }

    // 비즈니스 메서드 (퇴장)
    public void leave(User user) {
        if (user != null) {
            this.activeUsers.remove(user);
        }
    }
    
    /**
     * 외부에서 직접 컬렉션을 수정하지 못하도록 방어적 복사(혹은 unmodifiable) 반환
     */
    public Set<User> getActiveUsers() { 
        return Collections.unmodifiableSet(activeUsers); 
    }
}
