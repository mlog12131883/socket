package com.socket.server.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Redis 기반 L2 글로벌 캐시 Leaf 구현체.
 * Spring Cache 인터페이스를 구현하여 @Cacheable 등의 추상화와 호환됩니다.
 *
 * <p>구형 데이터(타입 정보 누락 등)로 인한 역직렬화 오류 발생 시,
 * 해당 키를 자동 삭제하고 캐시 미스로 처리하는 자기 복구(Self-Healing) 메커니즘을 내장합니다.
 * 이를 통해 FLUSHDB 없이도 안전하게 서버를 재시작할 수 있습니다.</p>
 */
public class RedisCache implements org.springframework.cache.Cache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);

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

    /**
     * Redis에서 안전하게 값을 읽어옵니다.
     * 역직렬화 실패(구형 데이터 등) 시 해당 키를 자동 삭제하고 null을 반환합니다.
     */
    private Object safeGet(Object key) {
        String rk = redisKey(key);
        try {
            return redisTemplate.opsForValue().get(rk);
        } catch (Exception e) {
            log.warn("[RedisCache] Deserialization failed for key [{}]. Auto-evicting stale data: {}",
                    rk, e.getMessage());
            try { redisTemplate.delete(rk); } catch (Exception ignored) {}
            return null;
        }
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
        Object value = safeGet(key);
        return (value != null) ? new SimpleValueWrapper(value) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = safeGet(key);
        if (value == null) return null;
        if (type != null && !type.isInstance(value)) {
            // 타입 불일치 -> 구형 데이터이므로 자동 삭제 후 캐시 미스 처리
            log.warn("[RedisCache] Type mismatch for key [{}]: expected [{}], actual [{}]. Auto-evicting.",
                    redisKey(key), type.getName(), value.getClass().getName());
            evict(key);
            return null;
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object existing = safeGet(key);
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
