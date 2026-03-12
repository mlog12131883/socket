package com.socket.server.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 라이브러리를 사용한 글로벌 분산 캐시 저장소 구현체 (L2 캐시)
 */
@Repository
public class DistributedCacheRepository<K, V> implements CacheRepository<K, V> {

    private final RedisTemplate<K, V> redisTemplate;

    public DistributedCacheRepository(RedisTemplate<K, V> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(K key, V value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * TTL(Time-To-Live) 설정을 포함한 저장 메서드
     */
    public void save(K key, V value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @Override
    public Optional<V> findById(K key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void deleteById(K key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean existsById(K key) {
        Boolean hasKey = redisTemplate.hasKey(key);
        return hasKey != null && hasKey;
    }
}
