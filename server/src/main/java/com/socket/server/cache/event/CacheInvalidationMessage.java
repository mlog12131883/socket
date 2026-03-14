package com.socket.server.cache.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분산 서버 간 캐시 무효화(Invalidation) 이벤트를 전달하는 메시지 DTO.
 *
 * <p>Redis Pub/Sub 채널({@code global-cache-invalidation})을 통해 JSON으로 직렬화되어 전파됩니다.</p>
 *
 * <ul>
 *   <li>{@code cacheName}  : 무효화 대상 캐시 이름 (예: "sessions", "rooms")</li>
 *   <li>{@code cacheKey}   : 무효화 대상 데이터 키. null 이면 캐시 전체(clear) 무효화</li>
 *   <li>{@code sourceServerId} : 이벤트를 발행한 서버의 UUID.
 *       수신 측은 자신의 ID와 비교하여 자기가 발행한 이벤트는 무시합니다.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationMessage {

    /** 무효화 대상 캐시 이름 */
    private String cacheName;

    /** 무효화 대상 키 (null 이면 캐시 전체 clear) */
    private String cacheKey;

    /** 이벤트를 발행한 서버의 고유 UUID */
    private String sourceServerId;
}
