package io.github.ooo1208.application.websearch;

import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 联网搜索 application 模型的纯单元测试，不访问网络或 Provider。
 */
class NetworkSearchModelTest {

    @Test
    void queryTrimsTextAndKeepsRequestedLimit() {
        NetworkSearchQuery query = new NetworkSearchQuery("  rag  ", 3);

        assertEquals("rag", query.query());
        assertEquals(3, query.maxResults());
    }

    @Test
    void queryRejectsOutOfRangeLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NetworkSearchQuery("rag", 11)
        );
    }

    @Test
    void resultMustBeMarkedUntrusted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NetworkSearchResult(
                        "title",
                        "https://example.com",
                        "snippet",
                        false
                )
        );
    }
}
