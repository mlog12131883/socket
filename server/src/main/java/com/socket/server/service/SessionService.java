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
