package com.socket.server.cache.manager;

import org.springframework.cache.Cache;

/**
 * 하위(L2) 캐시에서 찾은 데이터를 상위(L1) 캐시에 밀어 넣기 위한 전용 인터페이스.
 *
 * <p>CompositeCache가 L2에서 데이터를 발견했을 때,
 * L1 CacheManager에게 putIfAbsent를 요청하여 L1을 갱신(Look-Aside write-back)합니다.</p>
 */
public interface UpdatableCacheManager extends org.springframework.cache.CacheManager {

    /**
     * 해당 캐시에 키가 없을 경우에만 값을 삽입합니다 (L1 자동 갱신용).
     *
     * @param cacheName 대상 캐시 이름
     * @param key       캐시 키
     * @param value     저장할 값
     */
    void putIfAbsent(String cacheName, Object key, Object value);
}
