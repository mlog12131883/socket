package com.socket.server.service;

import com.socket.server.domain.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Socket session management service (Uses Spring Cache abstraction + Self-Injection)
 */
@Service
@RequiredArgsConstructor
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final ObjectProvider<SessionService> selfProvider;

    private SessionService getSelf() {
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
        log.info("[SessionService] Session closed event received: {}", event.getUserId());
        getSelf().removeSession(event.getUserId());
    }

    /**
     * Register user session
     */
    @CachePut(value = "sessions", key = "#sessionId")
    public User registerSession(String sessionId, User user) {
        log.info("Registering session: sessionId={}, userId={}", sessionId, user.getId());
        return user;
    }

    /**
     * Get session information
     */
    @Cacheable(value = "sessions", key = "#sessionId")
    public Optional<User> getSession(String sessionId) {
        log.debug("Cache miss for session: {}", sessionId);
        return Optional.empty();
    }

    /**
     * Remove session and handle disconnection
     */
    @CacheEvict(value = "sessions", key = "#sessionId")
    public void removeSession(String sessionId) {
        getSelf().getSession(sessionId).ifPresent(user -> {
            log.info("Removing session and disconnecting user: sessionId={}, userId={}", sessionId, user.getId());
            user.disconnect();
        });
    }

    /**
     * Check if session is active
     */
    public boolean isSessionActive(String sessionId) {
        return getSelf().getSession(sessionId).isPresent();
    }
}
