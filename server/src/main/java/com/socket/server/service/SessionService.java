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
 * 소켓 세션 관리 서비스 (Spring Cache 추상화 + Self-Injection 사용)
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
        // Observer 패턴: 세션 종료 이벤트 구독
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
     * 사용자 세션 등록
     */
    @CachePut(value = "sessions", key = "#sessionId")
    public User registerSession(String sessionId, User user) {
        log.info("Registering session: sessionId={}, userId={}", sessionId, user.getId());
        return user;
    }

    /**
     * 세션 정보 조회.
     *
     * <p>Optional 대신 직접 객체를 리턴하여 
     * 직렬화 시 발생할 수 있는 잠재적 ClassCastException을 방지합니다.</p>
     */
    @Cacheable(value = "sessions", key = "#sessionId")
    public User getSession(String sessionId) {
        log.debug("Cache miss for session: {}", sessionId);
        return null;
    }

    /**
     * 세션 제거 및 연결 해제 처리
     */
    @CacheEvict(value = "sessions", key = "#sessionId")
    public void removeSession(String sessionId) {
        User user = getSelf().getSession(sessionId);
        if (user != null) {
            log.info("Removing session and disconnecting user: sessionId={}, userId={}", sessionId, user.getId());
            user.disconnect();
        }
    }

    /**
     * 세션 활성 여부 확인
     */
    public boolean isSessionActive(String sessionId) {
        return getSelf().getSession(sessionId) != null;
    }
}
