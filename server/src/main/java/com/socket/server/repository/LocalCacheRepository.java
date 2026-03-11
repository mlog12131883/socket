package com.socket.server.repository;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Caffeine 라이브러리를 사용한 로컬 캐시 저장소 구현체 (L1 캐시)
 */
public class LocalCacheRepository<K, V> implements CacheRepository<K, V> {

    private final Cache<K, V> cache;

    public LocalCacheRepository(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public void save(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public Optional<V> findById(K key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void deleteById(K key) {
        cache.invalidate(key);
    }

    @Override
    public boolean existsById(K key) {
        return cache.getIfPresent(key) != null;
    }
}
