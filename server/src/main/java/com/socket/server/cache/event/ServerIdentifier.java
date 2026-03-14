package com.socket.server.cache.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 현재 서버의 고유 식별자(UUID)를 관리하는 컴포넌트.
 *
 * <p>서버 기동 시 한 번 생성되는 UUID를 보유하며,
 * {@link CacheInvalidationListener}가 자신이 발행한 이벤트를 재처리하지 않도록
 * {@code sourceServerId}와 비교하는 데 사용됩니다.</p>
 */
@Component
public class ServerIdentifier {

    private static final Logger log = LoggerFactory.getLogger(ServerIdentifier.class);

    private final String id;

    public ServerIdentifier() {
        this.id = UUID.randomUUID().toString();
        log.info("[ServerIdentifier] This server's unique ID: {}", id);
    }

    /** 현재 서버의 고유 UUID 반환 */
    public String getId() {
        return id;
    }
}
