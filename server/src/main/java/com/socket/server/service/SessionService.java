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
 * 소켓 세션 관리 서비스 (Spring Cache 추상화 + Self-Injection 활용)
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
        // Observer 패턴: 연결 종료 이벤트 구독
        com.socket.server.event.EventBus.getInstance().subscribe(event -> {
            if (event instanceof com.socket.server.event.SessionClosedEvent) {
                handleSessionClosed((com.socket.server.event.SessionClosedEvent) event);
            }
        });
    }

    private void handleSessionClosed(com.socket.server.event.SessionClosedEvent event) {
        log.info("[SessionService] 연결 종료 이벤트 수신: {}", event.getUserId());
        getSelf().removeSession(event.getUserId());
    }

    /**
     * 사용자 세션 등록
     */
    @CachePut(value = "sessions", key = "#sessionId")
    public User registerSession(String sessionId, User user) {
        log.info("Registering session: sessionId={}, userId={}", sessionId, user.getId());
        return user;
    }

    /**
     * 세션 정보 조회
     */
    @Cacheable(value = "sessions", key = "#sessionId")
    public Optional<User> getSession(String sessionId) {
        log.debug("Cache miss for session: {}", sessionId);
        return Optional.empty();
    }

    /**
     * 세션 제거 및 연결 종료 처리
     */
    @CacheEvict(value = "sessions", key = "#sessionId")
    public void removeSession(String sessionId) {
        getSelf().getSession(sessionId).ifPresent(user -> {
            log.info("Removing session and disconnecting user: sessionId={}, userId={}", sessionId, user.getId());
            user.disconnect();
        });
    }

    /**
     * 세션 존재 여부 확인
     */
    public boolean isSessionActive(String sessionId) {
        return getSelf().getSession(sessionId).isPresent();
    }
}
