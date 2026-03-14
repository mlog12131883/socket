package com.socket.server.config;

import com.socket.server.cache.CacheGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 서버 기동 시 Redis 캐시 네임스페이스를 자동 정리하는 리스너.
 *
 * <p>이전 서버 세션에서 남은 구형 데이터(타입 정보 누락 등)로 인한
 * ClassCastException을 원천 차단합니다.</p>
 *
 * <p>FLUSHDB 대신 캐시 키 패턴(rooms::*, sessions::*)만 선택적으로 삭제하므로
 * Redis에 저장된 다른 데이터(Pub/Sub 설정 등)에는 영향을 주지 않습니다.</p>
 */
@Component
public class CacheWarmUpListener {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmUpListener.class);

    private final RedisTemplate<String, Object> cacheRedisTemplate;

    public CacheWarmUpListener(RedisTemplate<String, Object> cacheRedisTemplate) {
        this.cacheRedisTemplate = cacheRedisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[CacheWarmUp] Clearing stale cache data from previous server session...");

        int totalDeleted = 0;
        for (CacheGroup group : CacheGroup.values()) {
            String pattern = group.getCacheName() + "::*";
            Set<String> keys = cacheRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                cacheRedisTemplate.delete(keys);
                totalDeleted += keys.size();
                log.info("[CacheWarmUp] Cleared {} stale keys matching pattern [{}]", keys.size(), pattern);
            }
        }

        if (totalDeleted > 0) {
            log.info("[CacheWarmUp] Total {} stale cache entries cleared.", totalDeleted);
        } else {
            log.info("[CacheWarmUp] No stale cache data found. Cache is clean.");
        }
    }
}
