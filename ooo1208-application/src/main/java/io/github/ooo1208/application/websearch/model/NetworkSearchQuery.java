package io.github.ooo1208.application.websearch.model;

/**
 * 联网搜索查询。
 *
 * <p>查询只携带用户要搜索的文本和数量上限，不携带 URL、Provider 地址、
 * API Key 或 HTTP 选项。服务端的搜索适配器由配置选择，避免请求方绕过
 * 固定 Provider 直接发起 SSRF。</p>
 */
public record NetworkSearchQuery(String query, int maxResults) {

    public static final int MIN_QUERY_LENGTH = 1;
    public static final int MAX_QUERY_LENGTH = 256;
    public static final int MIN_RESULTS = 1;
    public static final int MAX_RESULTS = 10;
    public static final int DEFAULT_RESULTS = 5;

    public NetworkSearchQuery {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "search query must not be blank"
            );
        }
        query = query.trim();
        if (query.length() < MIN_QUERY_LENGTH
                || query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "search query length must be between "
                            + MIN_QUERY_LENGTH + " and " + MAX_QUERY_LENGTH
            );
        }
        if (maxResults < MIN_RESULTS || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException(
                    "maxResults must be between " + MIN_RESULTS
                            + " and " + MAX_RESULTS
            );
        }
    }

    public static NetworkSearchQuery of(String query) {
        return new NetworkSearchQuery(query, DEFAULT_RESULTS);
    }
}
