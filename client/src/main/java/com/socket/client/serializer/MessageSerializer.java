package com.socket.client.serializer;

public interface MessageSerializer<T> {
    byte[] serialize(T object);
    T deserialize(byte[] bytes, Class<T> clazz);
}
