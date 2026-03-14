package com.socket.server.cache;

/**
 * 캐시의 계층/종류를 나타내는 타입
 *
 * <ul>
 *   <li>LOCAL     - Caffeine 기반 JVM 내 L1 캐시</li>
 *   <li>GLOBAL    - Redis 기반 분산 L2 캐시</li>
 *   <li>COMPOSITE - L1 + L2 를 함께 사용하는 2-Level 복합 캐시</li>
 * </ul>
 */
public enum CacheType {
    LOCAL,
    GLOBAL,
    COMPOSITE
}
