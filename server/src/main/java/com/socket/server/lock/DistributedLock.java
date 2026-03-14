package com.socket.server.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 분산 락(Distributed Lock) 커스텀 어노테이션.
 *
 * <p>이 어노테이션이 붙은 메서드는 실행 전에 Redis 분산 락을 획득하고,
 * 실행 완료(또는 예외 발생) 후 안전하게 락을 해제합니다.</p>
 *
 * <p>key 속성은 SpEL(Spring Expression Language) 표현식을 지원합니다.
 * 메서드 파라미터를 동적으로 참조할 수 있습니다.</p>
 *
 * <pre>
 * 사용 예시:
 * {@literal @}DistributedLock(key = "'room:' + #roomId", waitTime = 5, leaseTime = 10)
 * public ChatRoom joinRoom(String roomId, User user) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키 (SpEL 표현식).
     * 예: "'room:' + #roomId"
     */
    String key();

    /**
     * 락 획득 최대 대기 시간 (초).
     * 이 시간 내에 락을 획득하지 못하면 LockAcquisitionException 발생.
     */
    long waitTime() default 5;

    /**
     * 락 자동 해제 시간 (초).
     * 서버 다운 등으로 unlock()이 호출되지 않아도 이 시간 후 자동 해제되어 데드락 방지.
     */
    long leaseTime() default 10;
}
