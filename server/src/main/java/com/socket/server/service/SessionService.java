package com.socket.server.service;

import com.socket.server.domain.User;
import com.socket.server.repository.CacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 소켓 세션 관리 서비스 (L1 캐시 활용)
 */
@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final CacheRepository<String, User> sessionCache;

    public SessionService(@Qualifier("sessionCache") CacheRepository<String, User> sessionCache) {
        this.sessionCache = sessionCache;
        
        // Observer 패턴: 연결 종료 이벤트 구독
        com.socket.server.event.EventBus.getInstance().subscribe(event -> {
            if (event instanceof com.socket.server.event.SessionClosedEvent) {
                handleSessionClosed((com.socket.server.event.SessionClosedEvent) event);
            }
        });
    }

    private void handleSessionClosed(com.socket.server.event.SessionClosedEvent event) {
        log.info("[SessionService] 연결 종료 이벤트 수신: {}", event.getUserId());
        // 세션 아이디가 유저 아이디와 동일하다고 가정하거나, 
        // 맵핑 정보를 찾아 세션 제거
        removeSession(event.getUserId());
    }

    /**
     * 사용자 세션 등록
     */
    public void registerSession(String sessionId, User user) {
        log.info("Registering session: sessionId={}, userId={}", sessionId, user.getId());
        sessionCache.save(sessionId, user);
    }

    /**
     * 세션 정보 조회
     */
    public Optional<User> getSession(String sessionId) {
        return sessionCache.findById(sessionId);
    }

    /**
     * 세션 제거 및 연결 종료 처리
     */
    public void removeSession(String sessionId) {
        sessionCache.findById(sessionId).ifPresent(user -> {
            log.info("Removing session: sessionId={}, userId={}", sessionId, user.getId());
            user.disconnect();
            sessionCache.deleteById(sessionId);
        });
    }

    /**
     * 세션 존재 여부 확인
     */
    public boolean isSessionActive(String sessionId) {
        return sessionCache.existsById(sessionId);
    }
}
