package com.socket.server.exception;

/**
 * 채팅방 수용 인원 초과 시 발생하는 예외.
 *
 * <p>분산 락으로 보호된 임계 구역 내에서 인원 제한 검증 실패 시 던져집니다.</p>
 */
public class RoomFullException extends RuntimeException {

    public RoomFullException(String roomId, int maxCapacity) {
        super("Room [" + roomId + "] is full. Max capacity: " + maxCapacity);
    }
}
