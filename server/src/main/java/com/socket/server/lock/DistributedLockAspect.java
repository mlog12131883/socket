package com.socket.server.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락 AOP Aspect.
 *
 * <p>{@link DistributedLock} 어노테이션이 붙은 메서드를 가로채서
 * Redisson 분산 락을 자동으로 획득/해제합니다.</p>
 *
 * <p>핵심 동작 흐름:</p>
 * <ol>
 *   <li>SpEL 파서로 어노테이션의 key를 런타임에 동적 평가</li>
 *   <li>tryLock(waitTime, leaseTime, SECONDS)으로 락 획득 시도</li>
 *   <li>실패 시 LockAcquisitionException 발생</li>
 *   <li>성공 시 원래 메서드(joinPoint.proceed()) 실행</li>
 *   <li>finally 블록에서 안전하게 unlock()</li>
 * </ol>
 */
@Aspect
@Component
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);
    private static final String LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 1. SpEL 표현식을 런타임 값으로 치환
        String lockKey = LOCK_PREFIX + resolveKey(joinPoint, distributedLock.key());
        long waitTime = distributedLock.waitTime();
        long leaseTime = distributedLock.leaseTime();

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            // 2. 락 획득 시도 (Pub/Sub 방식 – 스핀 락 X, Redis 부하 최소화)
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("[DistributedLock] Failed to acquire lock: key={}, waitTime={}s", lockKey, waitTime);
                throw new com.socket.server.exception.LockAcquisitionException(
                        "Failed to acquire distributed lock for key: " + lockKey);
            }

            log.info("[DistributedLock] Lock acquired: key={}", lockKey);

            // 3. 원래 메서드 실행 (임계 구역)
            return joinPoint.proceed();

        } finally {
            // 4. 안전한 락 해제
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[DistributedLock] Lock released: key={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식을 평가하여 실제 락 키 문자열을 생성합니다.
     *
     * <p>메서드 파라미터 이름을 SpEL 컨텍스트에 등록하여
     * "#roomId" 같은 표현식이 실제 인자 값으로 치환됩니다.</p>
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String spelExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // 메서드 파라미터 이름 추출
        String[] parameterNames = nameDiscoverer.getParameterNames(method);
        if (parameterNames == null) {
            return spelExpression; // 파라미터 이름을 알 수 없으면 표현식 그대로 반환
        }

        // SpEL 평가 컨텍스트 구성
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            ((StandardEvaluationContext) context).setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(spelExpression).getValue(context, String.class);
    }
}
