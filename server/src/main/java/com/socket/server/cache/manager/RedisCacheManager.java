package com.socket.server.cache.manager;

import com.socket.server.cache.CacheGroup;
import com.socket.server.cache.CacheType;
import com.socket.server.cache.RedisCache;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 기반 L2 글로벌 캐시 매니저.
 *
 * <p>CacheGroup 중 GLOBAL 또는 COMPOSITE 타입에 해당하는 캐시를 관리합니다.</p>
 */
public class RedisCacheManager implements CacheManager {

    private final Map<String, RedisCache> cacheMap;

    public RedisCacheManager(RedisTemplate<String, Object> redisTemplate) {
        cacheMap = new ConcurrentHashMap<>();
        for (CacheGroup group : CacheGroup.values()) {
            if (group.getCacheType() == CacheType.GLOBAL
                    || group.getCacheType() == CacheType.COMPOSITE) {
                cacheMap.put(group.getCacheName(),
                        new RedisCache(group.getCacheName(), redisTemplate, group.getTtl()));
            }
        }
    }

    @Override
    public Cache getCache(String name) {
        return cacheMap.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheMap.keySet();
    }
}
