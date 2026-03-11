package com.socket.server.repository;

import java.util.Optional;

/**
 * 캐시 저장소 인터페이스
 * 특정 캐시 구현체(Caffeine, Redis 등)에 의존하지 않는 추상화 계층 제공
 */
public interface CacheRepository<K, V> {
    void save(K key, V value);
    Optional<V> findById(K key);
    void deleteById(K key);
    boolean existsById(K key);
}
