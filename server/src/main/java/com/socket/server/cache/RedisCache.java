package com.socket.server.cache;

import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Redis 기반 L2 글로벌 캐시 Leaf 구현체.
 * Spring Cache 인터페이스를 구현하여 @Cacheable 등의 추상화와 호환됩니다.
 */
public class RedisCache implements org.springframework.cache.Cache {

    private final String                         name;
    private final RedisTemplate<String, Object>  redisTemplate;
    private final Duration                       ttl;

    public RedisCache(String name, RedisTemplate<String, Object> redisTemplate, Duration ttl) {
        this.name          = name;
        this.redisTemplate = redisTemplate;
        this.ttl           = ttl;
    }

    // -------------------------------------------------------------------------
    // key 변환 헬퍼: name::key 형태로 Redis 키 네임스페이스 분리
    // -------------------------------------------------------------------------
    private String redisKey(Object key) {
        return name + "::" + key;
    }

    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return redisTemplate;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = redisTemplate.opsForValue().get(redisKey(key));
        return (value != null) ? new SimpleValueWrapper(value) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(redisKey(key));
        if (value == null) return null;
        if (type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object existing = redisTemplate.opsForValue().get(redisKey(key));
        if (existing != null) return (T) existing;
        try {
            T loaded = valueLoader.call();
            put(key, loaded);
            return loaded;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) return;
        redisTemplate.opsForValue().set(redisKey(key), value, ttl);
    }

    @Override
    public void evict(Object key) {
        redisTemplate.delete(redisKey(key));
    }

    @Override
    public void clear() {
        // clear는 위험 연산이므로 캐시 이름 패턴에만 적용
        var keys = redisTemplate.keys(name + "::*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
