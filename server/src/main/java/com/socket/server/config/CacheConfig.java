package com.socket.server.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.socket.server.domain.ChatRoom;
import com.socket.server.domain.User;
import com.socket.server.repository.CacheRepository;
import com.socket.server.repository.LocalCacheRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * 세션 정보 캐시 설정 (L1 캐시)
     * Maximum Size: 10,000 엔트리
     * TTL: 마지막 쓰기 후 30분 만료
     */
    @Bean
    public CacheRepository<String, User> sessionCache() {
        return new LocalCacheRepository<>(
                Caffeine.newBuilder()
                        .maximumSize(10000)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats() // 캐시 성능 모니터링용
                        .build()
        );
    }

    /**
     * 채팅방 메타데이터 캐시 설정 (L1 캐시)
     * Maximum Size: 1,000 엔트리
     * TTL: 마지막 접근 후 1시간 만료 (핫 데이터 특성 반영)
     */
    @Bean
    public CacheRepository<String, ChatRoom> roomCache() {
        return new LocalCacheRepository<>(
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterAccess(1, TimeUnit.HOURS)
                        .recordStats()
                        .build()
        );
    }
}
