package io.github.ooo1208.application.websearch;

import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import io.github.ooo1208.application.websearch.service.NetworkSearchApplicationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 固定 Provider 搜索缓存测试，不访问真实网络。
 */
class NetworkSearchApplicationServiceTest {

    private static final NetworkSearchResult RESULT = new NetworkSearchResult(
            "Spring AI",
            "https://example.com/spring-ai",
            "safe snippet",
            true
    );

    @Test
    void repeatedSameQueryUsesBoundedCache() {
        AtomicInteger calls = new AtomicInteger();
        NetworkSearchApplicationService service = newService(
                query -> {
                    calls.incrementAndGet();
                    return List.of(RESULT);
                },
                2
        );

        assertEquals(List.of(RESULT), service.search(query(3)));
        assertEquals(List.of(RESULT), service.search(query(3)));
        assertEquals(1, calls.get());
    }

    @Test
    void resultLimitIsPartOfCacheKeyAndLruEvictsOldEntry() {
        AtomicInteger calls = new AtomicInteger();
        NetworkSearchApplicationService service = newService(
                query -> {
                    calls.incrementAndGet();
                    return List.of(RESULT);
                },
                1
        );

        service.search(query(2));
        service.search(query(3));
        service.search(query(2));

        assertEquals(3, calls.get());
    }

    @Test
    void providerFailureIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        NetworkSearchApplicationService service = newService(
                query -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("provider down");
                },
                2
        );

        assertThrows(IllegalStateException.class, () -> service.search(query(3)));
        assertThrows(IllegalStateException.class, () -> service.search(query(3)));
        assertEquals(2, calls.get());
    }

    private NetworkSearchApplicationService newService(
            io.github.ooo1208.application.websearch.port.out.NetworkSearchPort port,
            int maxEntries
    ) {
        return new NetworkSearchApplicationService(
                port,
                Duration.ofMinutes(1),
                maxEntries,
                Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    private NetworkSearchQuery query(int maxResults) {
        return new NetworkSearchQuery("spring ai", maxResults);
    }
}
