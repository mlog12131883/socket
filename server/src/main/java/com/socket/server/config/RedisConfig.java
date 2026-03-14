package com.socket.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.socket.server.cache.event.CacheInvalidationListener;
import com.socket.server.domain.ChatMessage;
import com.socket.server.serializer.GzipRedisSerializer;
import com.socket.server.service.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Pub/Sub 메시지 전송용 ObjectMapper (ChatMessage 등 단순 JSON 직렬화).
     * 타입 정보를 포함하지 않으므로 Pub/Sub 접수자(RedisSubscriber)에서 직접 타입을 지정하여 역직렬화 합니다.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Cache 전용 ObjectMapper.
     *
     * <p>{@code activateDefaultTyping}을 활성화하여
     * 직렬화 시 JSON에 {@code @class} 필드로 타입 정보를 포함시킵니다.
     * 덕분에 역직렬화 시 {@code LinkedHashMap}이 아니라
     * {@code ChatRoom}, {@code User} 등 실제 도메인 타입으로 복원됩니다.</p>
     */
    @Bean
    public ObjectMapper cacheObjectMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator
                .builder()
                .allowIfBaseType(Object.class)
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING);
        return mapper;
    }

    /**
     * Pub/Sub 메시지 전송용 RedisTemplate (기존 용도 유지).
     * ChatMessage를 JSON으로 직렬화합니다.
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        return template;
    }

    /**
     * Cache 전용 RedisTemplate (GzipRedisSerializer 적용).
     * JSON 직렬화 후 Gzip 압축하여 Redis 네트워크 I/O 및 메모리를 최적화합니다.
     * cacheObjectMapper를 사용하여 타입 정보가 JSON에 포함되므로
     * 역직렬화 시 실제 도메인 타입(ChatRoom, User 등)으로 정확히 복원됩니다.
     */
    @Bean
    public RedisTemplate<String, Object> cacheRedisTemplate(RedisConnectionFactory connectionFactory,
                                                             ObjectMapper cacheObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GzipRedisSerializer(cacheObjectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GzipRedisSerializer(cacheObjectMapper));
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            CacheInvalidationListener cacheInvalidationListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 채팅 메시지 구독
        container.addMessageListener(listenerAdapter, new ChannelTopic("chat:rooms"));
        // 글로벌 캐시 무효화 이벤트 구독
        container.addMessageListener(
                new MessageListenerAdapter(cacheInvalidationListener, "onMessage"),
                new ChannelTopic("global-cache-invalidation"));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * 캐시 무효화 이벤트 발행용 StringRedisTemplate.
     * StringRedisSerializer를 사용하여 JSON 문자열을 raw bytes로 그대로 Redis에 발행합니다.
     * GenericJackson2JsonRedisSerializer가 String을 추가 JSON 인코딩하는 이중 직렬화 문제를 방지합니다.
     */
    @Bean
    public org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        return new org.springframework.data.redis.core.StringRedisTemplate(connectionFactory);
    }
}

