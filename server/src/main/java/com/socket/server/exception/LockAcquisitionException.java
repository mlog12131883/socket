package com.socket.server.exception;

/**
 * 분산 락 획득 실패(타임아웃) 시 발생하는 예외.
 *
 * <p>정해진 waitTime 내에 락을 획득하지 못하면 무한 대기 방지를 위해 즉시 실패 처리합니다.</p>
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }
}
