package io.github.ooo1208.application.websearch.service;

import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import io.github.ooo1208.application.websearch.port.in.SearchWebUseCase;
import io.github.ooo1208.application.websearch.port.out.NetworkSearchPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 联网搜索用例编排器。
 *
 * <p>Provider 是否启用、HTTP 请求、凭证和出站网络策略都属于基础设施层；
 * application 层只校验查询并保证返回集合不可变。</p>
 */
public final class NetworkSearchApplicationService
        implements SearchWebUseCase {

    private final NetworkSearchPort networkSearchPort;
    private final Duration cacheTtl;
    private final Map<CacheKey, CacheEntry> cache;
    private final Clock clock;

    public NetworkSearchApplicationService(NetworkSearchPort networkSearchPort) {
        this(
                networkSearchPort,
                Duration.ofSeconds(30),
                128,
                Clock.systemUTC()
        );
    }

    public NetworkSearchApplicationService(
            NetworkSearchPort networkSearchPort,
            Duration cacheTtl,
            int maxCacheEntries,
            Clock clock
    ) {
        this.networkSearchPort = Objects.requireNonNull(networkSearchPort);
        this.cacheTtl = Objects.requireNonNull(cacheTtl);
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "network search cache TTL must be positive"
            );
        }
        if (maxCacheEntries < 1 || maxCacheEntries > 10_000) {
            throw new IllegalArgumentException(
                    "network search cache entries must be between 1 and 10000"
            );
        }
        this.clock = Objects.requireNonNull(clock);
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<CacheKey, CacheEntry> eldest
            ) {
                return size() > maxCacheEntries;
            }
        };
    }

    @Override
    public List<NetworkSearchResult> search(NetworkSearchQuery query) {
        Objects.requireNonNull(query, "query");
        CacheKey cacheKey = new CacheKey(query.query(), query.maxResults());
        Instant now = clock.instant();
        synchronized (cache) {
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null) {
                if (now.isBefore(cached.expiresAt())) {
                    return cached.results();
                }
                cache.remove(cacheKey);
            }
        }

        List<NetworkSearchResult> results = networkSearchPort.search(query);
        List<NetworkSearchResult> immutableResults = results == null
                ? List.of()
                : List.copyOf(results);
        synchronized (cache) {
            cache.put(
                    cacheKey,
                    new CacheEntry(
                            immutableResults,
                            clock.instant().plus(cacheTtl)
                    )
            );
        }
        return immutableResults;
    }

    private record CacheKey(String query, int maxResults) {
    }

    private record CacheEntry(
            List<NetworkSearchResult> results,
            Instant expiresAt
    ) {
    }
}
