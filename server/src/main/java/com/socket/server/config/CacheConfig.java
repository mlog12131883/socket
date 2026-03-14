package com.socket.server.config;

import com.socket.server.cache.event.CacheInvalidationPublisher;
import com.socket.server.cache.manager.CompositeCacheManager;
import com.socket.server.cache.manager.LocalCacheManager;
import com.socket.server.cache.manager.RedisCacheManager;
import com.socket.server.cache.manager.UpdatableCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

/**
 * Composite Pattern 기반 2-Level Cache 설정.
 *
 * <pre>
 * CompositeCacheManager  ← @Primary (Spring @Cacheable 연결점)
 *   ├─ LocalCacheManager   (L1 Caffeine, UpdatableCacheManager 구현)
 *   └─ RedisCacheManager   (L2 Redis)
 * </pre>
 *
 * 새로운 캐시 추가는 {@link com.socket.server.cache.CacheGroup}에 항목만 추가하면 됩니다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * L1 로컬 캐시 매니저 (Caffeine 기반).
     * UpdatableCacheManager 구현체이므로 CompositeCache에서 write-back에 활용됩니다.
     */
    @Bean
    public LocalCacheManager localCacheManager() {
        return new LocalCacheManager();
    }

    /**
     * L2 분산 캐시 매니저 (Redis 기반).
     * GzipRedisSerializer가 적용된 RedisTemplate을 사용합니다.
     */
    @Bean
    public RedisCacheManager redisCacheManager(RedisTemplate<String, Object> cacheRedisTemplate) {
        return new RedisCacheManager(cacheRedisTemplate);
    }

    /**
     * 최상위 복합 캐시 매니저.
     * Spring Cache 추상화(@Cacheable 등)의 실제 진입점입니다.
     *
     * <p>CacheGroup 타입이 COMPOSITE인 캐시는 L1 + L2 를 모두 활용하며,
     * L2 hit 시 자동으로 L1에 write-back됩니다.
     * put/evict 시 {@link CacheInvalidationPublisher}를 통해 타 서버에 무효화 이벤트를 발행합니다.</p>
     */
    @Bean
    @Primary
    public CacheManager cacheManager(LocalCacheManager localCacheManager,
                                     RedisCacheManager redisCacheManager,
                                     CacheInvalidationPublisher cacheInvalidationPublisher) {
        return new CompositeCacheManager(
                localCacheManager,
                cacheInvalidationPublisher,
                List.of(localCacheManager, redisCacheManager)
        );
    }
}
