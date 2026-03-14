package com.socket.server.cache;

import com.socket.server.cache.manager.UpdatableCacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Composite Pattern 적용 복합 캐시 (L1 + L2 를 하나의 Cache 인터페이스로 통합).
 *
 * <h3>Look-Aside 조회 전략</h3>
 * <ol>
 *   <li>L1(Caffeine) 에서 먼저 탐색한다.</li>
 *   <li>L1 miss 이면 L2(Redis) 에서 탐색한다.</li>
 *   <li>L2 hit 이면, L1 CacheManager의 {@code putIfAbsent}를 호출하여 L1에 즉시 갱신한다.</li>
 * </ol>
 *
 * <h3>쓰기/삭제 동기화</h3>
 * put / evict 작업 시 내부 캐시 리스트 전체를 순회하여 동시에 적용합니다.
 */
public class CompositeCache implements Cache {

    private final String                  name;
    private final List<Cache>             caches;          // index 0 = L1, index 1 = L2
    private final UpdatableCacheManager   l1Manager;       // L1 write-back을 위한 참조

    /**
     * @param name      캐시 이름
     * @param caches    순서가 중요 – 첫 번째가 L1(Local), 두 번째가 L2(Redis)
     * @param l1Manager L2 hit 시 L1에 putIfAbsent 하기 위한 UpdatableCacheManager
     */
    public CompositeCache(String name, List<Cache> caches, UpdatableCacheManager l1Manager) {
        this.name      = name;
        this.caches    = caches;
        this.l1Manager = l1Manager;
    }

    // -------------------------------------------------------------------------
    // 조회 – Look-Aside + L1 write-back
    // -------------------------------------------------------------------------

    @Override
    public ValueWrapper get(Object key) {
        for (int i = 0; i < caches.size(); i++) {
            Cache cache = caches.get(i);
            ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                // L2(i > 0)에서 찾았으면 → L1에 write-back
                if (i > 0) {
                    l1Manager.putIfAbsent(name, key, wrapper.get());
                }
                return wrapper;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) return null;
        Object value = wrapper.get();
        if (type != null && value != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) return (T) wrapper.get();
        try {
            T loaded = valueLoader.call();
            put(key, loaded);
            return loaded;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    // -------------------------------------------------------------------------
    // 쓰기/삭제 – 모든 하위 캐시에 동시 적용
    // -------------------------------------------------------------------------

    @Override
    public void put(Object key, Object value) {
        caches.forEach(cache -> cache.put(key, value));
    }

    @Override
    public void evict(Object key) {
        caches.forEach(cache -> cache.evict(key));
    }

    @Override
    public void clear() {
        caches.forEach(Cache::clear);
    }

    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return caches;
    }
}
