package com.socket.server.cache;

/**
 * 캐시 이름 상수 - 문자열 오타로 인한 런타임 에러 방지
 */
public final class CacheName {

    public static final String SESSIONS = "sessions";
    public static final String ROOMS    = "rooms";

    private CacheName() {}
}
