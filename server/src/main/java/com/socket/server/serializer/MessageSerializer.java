package com.socket.server.serializer;

/**
 * 메시지 직렬화 인터페이스
 * 자바 객체 <-> JSON Byte Array 간의 변환을 담당
 */
public interface MessageSerializer<T> {
    
    /**
     * 객체를 바이트 배열로 직렬화
     */
    byte[] serialize(T object);

    /**
     * 바이트 배열을 객체로 역직렬화
     */
    <R> R deserialize(byte[] bytes, Class<R> clazz);
}
