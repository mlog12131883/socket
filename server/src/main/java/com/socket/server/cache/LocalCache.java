package com.socket.server.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.concurrent.Callable;

/**
 * Caffeine 기반 L1 로컬 캐시 Leaf 구현체.
 * Spring Cache 인터페이스를 구현하여 @Cacheable 등의 추상화와 호환됩니다.
 */
public class LocalCache implements org.springframework.cache.Cache {

    private final String              name;
    private final Cache<Object, Object> store;

    public LocalCache(String name, Cache<Object, Object> store) {
        this.name  = name;
        this.store = store;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return store;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = store.getIfPresent(key);
        return (value != null) ? new SimpleValueWrapper(value) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = store.getIfPresent(key);
        if (value == null) return null;
        if (type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        return (T) store.get(key, k -> {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        });
    }

    @Override
    public void put(Object key, Object value) {
        // Caffeine은 null value를 허용하지 않음.
        // Spring 6+는 @Cacheable 메서드가 Optional.empty()를 반환할 때 null로 언래핑하여 put을 호출하므로
        // null인 경우 캐싱을 건너뜁니다 (RedisCache와 동일한 정책).
        if (value == null) return;
        store.put(key, value);
    }

    @Override
    public void evict(Object key) {
        store.invalidate(key);
    }

    @Override
    public void clear() {
        store.invalidateAll();
    }
}
