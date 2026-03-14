package com.socket.server.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * JSON 직렬화 후 Gzip 압축하여 Redis에 저장하는 커스텀 직렬화기.
 *
 * <h3>직렬화 순서</h3>
 * Object → Jackson JSON bytes → GZIP compress → Redis bytes
 *
 * <h3>역직렬화 순서</h3>
 * Redis bytes → GZIP decompress → Jackson JSON bytes → Object
 *
 * <h3>도입 효과</h3>
 * <ul>
 *   <li>기본 Java 직렬화의 패키지 경로 노출 및 용량 문제 해결</li>
 *   <li>JSON 텍스트 데이터는 Gzip 압축 시 일반적으로 70~80% 압축률 확보</li>
 *   <li>Redis 네트워크 I/O 및 메모리 절감</li>
 * </ul>
 */
public class GzipRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper objectMapper;

    public GzipRedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) return null;
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(value);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(jsonBytes);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Gzip 직렬화 실패: " + value.getClass().getName(), e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] jsonBytes = gzip.readAllBytes();
            return objectMapper.readValue(jsonBytes, Object.class);
        } catch (IOException e) {
            throw new SerializationException("Gzip 역직렬화 실패", e);
        }
    }
}
