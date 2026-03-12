package com.socket.server.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Global distributed cache repository implementation using the Redis library (L2 cache)
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
     * Storage method including TTL (Time-To-Live) settings
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
