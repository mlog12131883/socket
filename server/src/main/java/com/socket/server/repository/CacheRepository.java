package com.socket.server.repository;

import java.util.Optional;

/**
 * Cache repository interface
 * Provides an abstraction layer that does not depend on a specific cache implementation (Caffeine, Redis, etc.)
 */
public interface CacheRepository<K, V> {
    void save(K key, V value);
    Optional<V> findById(K key);
    void deleteById(K key);
    boolean existsById(K key);
}
