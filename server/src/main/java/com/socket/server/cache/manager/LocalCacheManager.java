package com.socket.server.cache.manager;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.socket.server.cache.CacheGroup;
import com.socket.server.cache.CacheType;
import com.socket.server.cache.LocalCache;
import org.springframework.cache.Cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine 기반 L1 로컬 캐시 매니저.
 *
 * <ul>
 *   <li>등록되지 않은 캐시 이름 요청 시 null 반환 (임의 생성 X) →
 *       다음 L2 조회 로직으로 자연스럽게 위임</li>
 *   <li>{@link UpdatableCacheManager} 구현 → L2 hit 시 L1 write-back 수행</li>
 * </ul>
 */
public class LocalCacheManager implements UpdatableCacheManager {

    private final Map<String, LocalCache> cacheMap = new ConcurrentHashMap<>();

    public LocalCacheManager() {
        // CacheGroup 기반으로 LOCAL 또는 COMPOSITE 타입 캐시 사전 생성
        for (CacheGroup group : CacheGroup.values()) {
            if (group.getCacheType() == CacheType.LOCAL
                    || group.getCacheType() == CacheType.COMPOSITE) {
                cacheMap.put(group.getCacheName(), buildCache(group));
            }
        }
    }

    private LocalCache buildCache(CacheGroup group) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache =
                Caffeine.newBuilder()
                        .maximumSize(group.getMaxSize())
                        .expireAfterWrite(group.getTtl())
                        .recordStats()
                        .build();
        return new LocalCache(group.getCacheName(), caffeineCache);
    }

    // -------------------------------------------------------------------------

    @Override
    public Cache getCache(String name) {
        // 등록된 캐시가 없으면 null 반환 → CompositeCacheManager가 L2로 넘어감
        return cacheMap.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheMap.keySet();
    }

    // -------------------------------------------------------------------------
    // UpdatableCacheManager – L2 hit 시 호출되는 L1 write-back
    // -------------------------------------------------------------------------

    @Override
    public void putIfAbsent(String cacheName, Object key, Object value) {
        LocalCache cache = cacheMap.get(cacheName);
        if (cache != null && cache.get(key) == null) {
            cache.put(key, value);
        }
    }
}
