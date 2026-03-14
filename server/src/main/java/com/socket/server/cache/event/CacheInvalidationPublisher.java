package com.socket.server.cache.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub을 통해 캐시 무효화(Invalidation) 이벤트를 발행하는 Publisher.
 *
 * <p>{@link com.socket.server.cache.CompositeCache}의 {@code put} 및 {@code evict} 이후
 * 이 클래스를 통해 {@code global-cache-invalidation} 채널로 이벤트를 전파합니다.</p>
 *
 * <p>수신 측({@link CacheInvalidationListener})은 자신이 발행한 이벤트를 {@code sourceServerId}로
 * 구분하여 무시하고, 타 서버의 이벤트만 처리합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationPublisher.class);

    /** 캐시 무효화 이벤트 전용 Redis 채널 이름 */
    public static final String CHANNEL = "global-cache-invalidation";

    private final StringRedisTemplate stringRedisTemplate; // StringRedisSerializer → 이중 직렬화 없음
    private final ObjectMapper         objectMapper;
    private final ServerIdentifier     serverIdentifier;

    /**
     * 특정 키에 대한 무효화 이벤트를 발행합니다 (put / evict 후 호출).
     *
     * @param cacheName 무효화 대상 캐시 이름
     * @param cacheKey  무효화 대상 캐시 키
     */
    public void publish(String cacheName, Object cacheKey) {
        CacheInvalidationMessage message = new CacheInvalidationMessage(
                cacheName,
                cacheKey != null ? cacheKey.toString() : null,
                serverIdentifier.getId()
        );
        doPublish(message);
    }

    /**
     * 캐시 전체 무효화 이벤트를 발행합니다 (clear 후 호출).
     *
     * @param cacheName 무효화 대상 캐시 이름
     */
    public void publishClear(String cacheName) {
        // cacheKey = null 이면 수신 측에서 캐시 전체 clear 처리
        CacheInvalidationMessage message = new CacheInvalidationMessage(
                cacheName,
                null,
                serverIdentifier.getId()
        );
        doPublish(message);
    }

    // -------------------------------------------------------------------------

    private void doPublish(CacheInvalidationMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            // StringRedisTemplate은 StringRedisSerializer를 사용하므로
            // JSON 문자열을 추가 인코딩 없이 raw UTF-8 bytes로 발행합니다.
            stringRedisTemplate.convertAndSend(CHANNEL, json);
            log.debug("[CacheInvalidationPublisher] Published: channel={}, cacheName={}, key={}",
                    CHANNEL, message.getCacheName(), message.getCacheKey());
        } catch (JsonProcessingException e) {
            log.error("[CacheInvalidationPublisher] Failed to serialize invalidation message: {}", e.getMessage(), e);
        }
    }
}
