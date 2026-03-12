package com.socket.server.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat room domain class
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

    // Business method (Enter)
    public void enter(User user) {
        if (user != null) {
            this.activeUsers.add(user);
        }
    }

    // Business method (Leave)
    public void leave(User user) {
        if (user != null) {
            this.activeUsers.remove(user);
        }
    }
    
    /**
     * Return defensive copy (or unmodifiable) to prevent external modification of the collection
     */
    public Set<User> getActiveUsers() { 
        return Collections.unmodifiableSet(activeUsers); 
    }
}
