package com.socket.server.cache.manager;

import com.socket.server.cache.CacheGroup;
import com.socket.server.cache.CacheType;
import com.socket.server.cache.CompositeCache;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.*;

/**
 * 최상위 복합 캐시 매니저 (Composite CacheManager).
 *
 * <h3>라우팅 로직</h3>
 * <ol>
 *   <li>요청된 캐시 이름에 해당하는 {@link CacheGroup}을 조회한다.</li>
 *   <li>타입이 COMPOSITE이면 내부 매니저 목록을 순회하며 캐시 객체를 수집한 후
 *       {@link CompositeCache}로 감싸서 반환한다.</li>
 *   <li>타입이 LOCAL 또는 GLOBAL이면 해당 매니저에서 직접 캐시를 반환한다.</li>
 * </ol>
 */
public class CompositeCacheManager implements CacheManager {

    private final List<CacheManager>  managers;
    private final UpdatableCacheManager l1Manager;

    /**
     * @param l1Manager L2 hit 시 L1 write-back에 사용될 UpdatableCacheManager
     * @param managers  순서 중요 – 첫 번째가 L1, 두 번째가 L2
     */
    public CompositeCacheManager(UpdatableCacheManager l1Manager, List<CacheManager> managers) {
        this.l1Manager = l1Manager;
        this.managers  = managers;
    }

    @Override
    public Cache getCache(String name) {
        CacheGroup group = findGroup(name);
        if (group == null) return null;

        if (group.getCacheType() == CacheType.COMPOSITE) {
            // 모든 매니저에서 캐시 수집 → CompositeCache로 조합
            List<Cache> collected = new ArrayList<>();
            for (CacheManager manager : managers) {
                Cache cache = manager.getCache(name);
                if (cache != null) collected.add(cache);
            }
            return collected.isEmpty() ? null : new CompositeCache(name, collected, l1Manager);
        }

        // LOCAL 또는 GLOBAL: 담당 매니저에서 직접 반환
        for (CacheManager manager : managers) {
            Cache cache = manager.getCache(name);
            if (cache != null) return cache;
        }

        return null;
    }

    @Override
    public Collection<String> getCacheNames() {
        Set<String> names = new LinkedHashSet<>();
        managers.forEach(m -> names.addAll(m.getCacheNames()));
        return Collections.unmodifiableSet(names);
    }

    // -------------------------------------------------------------------------

    private CacheGroup findGroup(String name) {
        for (CacheGroup group : CacheGroup.values()) {
            if (group.getCacheName().equals(name)) return group;
        }
        return null;
    }
}
