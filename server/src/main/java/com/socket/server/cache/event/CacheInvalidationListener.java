package com.socket.server.cache.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socket.server.cache.manager.LocalCacheManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis {@code global-cache-invalidation} 채널 구독 리스너.
 *
 * <p>타 서버에서 발행된 캐시 무효화 이벤트를 수신하면,
 * 자신의 L1(Caffeine) 캐시에서 해당 엔트리를 즉시 삭제(Evict)합니다.</p>
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>메시지 수신 → JSON 역직렬화 → {@link CacheInvalidationMessage} 생성</li>
 *   <li>{@code sourceServerId}가 현재 서버 ID와 같으면 무시 (자기 루프 방지)</li>
 *   <li>다른 서버의 이벤트이면 {@code LocalCacheManager}의 해당 캐시에서 키 evict</li>
 *   <li>다음 조회 시 Look-Aside 패턴으로 L2(Redis) 최신 데이터 자동 복구</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final ObjectMapper       objectMapper;
    private final ServerIdentifier   serverIdentifier;
    private final LocalCacheManager  localCacheManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            CacheInvalidationMessage event = objectMapper.readValue(json, CacheInvalidationMessage.class);

            // 자신이 발행한 이벤트는 무시
            if (serverIdentifier.getId().equals(event.getSourceServerId())) {
                log.debug("[CacheInvalidationListener] Skipping own invalidation event: cacheName={}, key={}",
                        event.getCacheName(), event.getCacheKey());
                return;
            }

            log.info("[CacheInvalidationListener] Received invalidation from remote server: cacheName={}, key={}",
                    event.getCacheName(), event.getCacheKey());

            // L1(Caffeine) 캐시에서 즉시 삭제
            invalidateLocalCache(event);

        } catch (Exception e) {
            log.error("[CacheInvalidationListener] Failed to process invalidation message: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------

    private void invalidateLocalCache(CacheInvalidationMessage event) {
        org.springframework.cache.Cache l1Cache = localCacheManager.getCache(event.getCacheName());
        if (l1Cache == null) {
            log.warn("[CacheInvalidationListener] L1 cache not found: {}", event.getCacheName());
            return;
        }

        if (event.getCacheKey() == null) {
            // cacheKey == null → 캐시 전체 클리어
            l1Cache.clear();
            log.info("[CacheInvalidationListener] L1 cache cleared: cacheName={}", event.getCacheName());
        } else {
            // 특정 키만 evict
            l1Cache.evict(event.getCacheKey());
            log.info("[CacheInvalidationListener] L1 cache evicted: cacheName={}, key={}",
                    event.getCacheName(), event.getCacheKey());
        }
    }
}
