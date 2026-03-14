package com.socket.server.cache;

import java.time.Duration;

/**
 * 캐시 메타데이터 중앙 집중 관리 Enum.
 *
 * <p>새로운 캐시가 필요할 경우 이 enum에 항목 하나만 추가하면 되며,
 * CacheManager 계층이 자동으로 해당 캐시를 구성합니다.</p>
 *
 * <pre>
 * 필드 순서: cacheName, cacheType, ttl, maxSize
 * </pre>
 */
public enum CacheGroup {

    SESSIONS(CacheName.SESSIONS, CacheType.COMPOSITE, Duration.ofMinutes(30), 10_000),
    ROOMS   (CacheName.ROOMS,    CacheType.COMPOSITE, Duration.ofHours(1),    1_000);

    // -------------------------------------------------------------------------

    private final String    cacheName;
    private final CacheType cacheType;
    private final Duration  ttl;
    private final long      maxSize;

    CacheGroup(String cacheName, CacheType cacheType, Duration ttl, long maxSize) {
        this.cacheName = cacheName;
        this.cacheType = cacheType;
        this.ttl       = ttl;
        this.maxSize   = maxSize;
    }

    public String    getCacheName() { return cacheName; }
    public CacheType getCacheType() { return cacheType; }
    public Duration  getTtl()       { return ttl;       }
    public long      getMaxSize()   { return maxSize;   }
}
