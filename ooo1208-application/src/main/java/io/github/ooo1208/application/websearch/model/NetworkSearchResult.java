package io.github.ooo1208.application.websearch.model;

/**
 * 搜索结果摘要。
 *
 * <p>联网内容始终是外部不可信数据。{@code untrusted} 明确提醒调用方：标题、
 * 摘要和链接只能作为引用展示，不能直接当作系统提示词或工具指令执行。</p>
 */
public record NetworkSearchResult(
        String title,
        String url,
        String snippet,
        boolean untrusted
) {

    public NetworkSearchResult {
        title = normalize(title, "title");
        url = normalize(url, "url");
        snippet = snippet == null ? "" : snippet.trim();
        if (!untrusted) {
            throw new IllegalArgumentException(
                    "network search results must be marked untrusted"
            );
        }
    }

    private static String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
        return value.trim();
    }
}
