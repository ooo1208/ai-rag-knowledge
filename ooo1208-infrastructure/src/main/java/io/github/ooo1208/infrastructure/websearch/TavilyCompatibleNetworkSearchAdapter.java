package io.github.ooo1208.infrastructure.websearch;

import io.github.ooo1208.application.websearch.exception.NetworkSearchDisabledException;
import io.github.ooo1208.application.websearch.exception.NetworkSearchProviderException;
import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import io.github.ooo1208.application.websearch.port.out.NetworkSearchPort;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import io.github.ooo1208.infrastructure.network.OutboundUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tavily-compatible只读联网搜索适配器。
 *
 * <p>第一版故意只支持一个固定 Provider：endpoint、凭证引用和启用开关都来自
 * Spring 配置，HTTP 请求不会接受来自调用方的 URL。默认关闭时仍注册一个
 * adapter bean，调用会得到明确的 {@link NetworkSearchDisabledException}，而不是
 * 因为缺少可选 Provider 导致整个应用无法启动。</p>
 *
 * <p>Provider 请求体使用 Tavily 约定的 {@code api_key}、{@code query} 和
 * {@code max_results} 字段。响应只提取标题、链接和摘要，且全部标记为不可信
 * 内容，不把外部正文直接拼接成系统指令。</p>
 */
@Component
public final class TavilyCompatibleNetworkSearchAdapter
        implements NetworkSearchPort {

    private static final int MIN_TIMEOUT_MILLIS = 100;
    private static final int MAX_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_SNIPPET_LENGTH = 2_000;
    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MIN_RESPONSE_BYTES = 4_096;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private final boolean enabled;
    private final String endpoint;
    private final String credentialRef;
    private final int timeoutMillis;
    private final int configuredMaxResults;
    private final int maxResponseBytes;
    private final CredentialResolver credentialResolver;

    public TavilyCompatibleNetworkSearchAdapter(
            @Value("${app.network-search.enabled:false}") boolean enabled,
            @Value("${app.network-search.endpoint:https://api.tavily.com/search}") String endpoint,
            @Value("${app.network-search.credential-ref:config:app.network-search.api-key}") String credentialRef,
            @Value("${app.network-search.timeout-ms:5000}") int timeoutMillis,
            @Value("${app.network-search.max-results:10}") int configuredMaxResults,
            @Value("${app.network-search.max-response-bytes:524288}") int maxResponseBytes,
            CredentialResolver credentialResolver
    ) {
        this.enabled = enabled;
        this.endpoint = requireText(endpoint, "network search endpoint");
        this.credentialRef = requireText(
                credentialRef,
                "network search credentialRef"
        );
        if (timeoutMillis < MIN_TIMEOUT_MILLIS
                || timeoutMillis > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException(
                    "network search timeout must be between "
                            + MIN_TIMEOUT_MILLIS + " and "
                            + MAX_TIMEOUT_MILLIS + " milliseconds"
            );
        }
        if (configuredMaxResults < NetworkSearchQuery.MIN_RESULTS
                || configuredMaxResults > NetworkSearchQuery.MAX_RESULTS) {
            throw new IllegalArgumentException(
                    "network search max-results must be between "
                            + NetworkSearchQuery.MIN_RESULTS + " and "
                    + NetworkSearchQuery.MAX_RESULTS
            );
        }
        if (maxResponseBytes < MIN_RESPONSE_BYTES
                || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException(
                    "network search max-response-bytes must be between "
                            + MIN_RESPONSE_BYTES + " and " + MAX_RESPONSE_BYTES
            );
        }
        this.timeoutMillis = timeoutMillis;
        this.configuredMaxResults = configuredMaxResults;
        this.maxResponseBytes = maxResponseBytes;
        this.credentialResolver = Objects.requireNonNull(credentialResolver);
    }

    @Override
    public List<NetworkSearchResult> search(NetworkSearchQuery query) {
        Objects.requireNonNull(query, "query");

        if (!enabled) {
            throw new NetworkSearchDisabledException();
        }

        OutboundUrlValidator.ValidatedUrl validatedEndpoint;
        try {
            validatedEndpoint = OutboundUrlValidator.validatePublicInternet(
                    endpoint
            );
        } catch (IllegalArgumentException exception) {
            throw new NetworkSearchProviderException(
                    "web search endpoint is not allowed by outbound policy",
                    exception
            );
        }

        String apiKey;
        try {
            apiKey = credentialResolver.resolve(credentialRef);
        } catch (IllegalArgumentException exception) {
            throw new NetworkSearchProviderException(
                    "web search credentials are not configured",
                    exception
            );
        }

        int resultLimit = Math.min(
                query.maxResults(),
                configuredMaxResults
        );

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .requestInterceptors(interceptors ->
                        interceptors.add(responseSizeInterceptor()))
                .build();

        TavilySearchResponse response;
        try {
            response = restClient.post()
                    .uri(validatedEndpoint.uri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "api_key", apiKey,
                            "query", query.query(),
                            "max_results", resultLimit
                    ))
                    .retrieve()
                    .body(TavilySearchResponse.class);
        } catch (RestClientResponseException exception) {
            throw new NetworkSearchProviderException(
                    "web search provider rejected the request"
            );
        } catch (ResourceAccessException exception) {
            throw new NetworkSearchProviderException(
                    "web search provider could not be reached",
                    exception
            );
        } catch (RestClientException exception) {
            throw new NetworkSearchProviderException(
                    "web search provider returned an unusable response",
                    exception
            );
        }

        return mapResults(response, resultLimit);
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        return requestFactory;
    }

    private ClientHttpRequestInterceptor responseSizeInterceptor() {
        return (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength > maxResponseBytes) {
                response.close();
                throw new IOException(
                        "web search provider response exceeds configured limit"
                );
            }
            return new BoundedClientHttpResponse(response, maxResponseBytes);
        };
    }

    private List<NetworkSearchResult> mapResults(
            TavilySearchResponse response,
            int resultLimit
    ) {
        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(Objects::nonNull)
                .map(this::mapResult)
                .filter(Objects::nonNull)
                .limit(resultLimit)
                .toList();
    }

    private NetworkSearchResult mapResult(TavilyResult result) {
        String title = trimToLength(result.title(), MAX_TITLE_LENGTH);
        String url = normalizeResultUrl(result.url());
        if (title == null || url == null) {
            return null;
        }

        String snippet = trimToLength(result.content(), MAX_SNIPPET_LENGTH);
        return new NetworkSearchResult(
                title,
                url,
                snippet == null ? "" : snippet,
                true
        );
    }

    private String normalizeResultUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http")
                    && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record TavilySearchResponse(List<TavilyResult> results) {
    }

    private record TavilyResult(
            String title,
            String url,
            String content
    ) {
    }

    /**
     * 限制响应流的读取上限，避免 Provider 返回异常大正文占满进程内存。
     */
    private static final class BoundedClientHttpResponse
            implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final int maxBytes;

        private BoundedClientHttpResponse(
                ClientHttpResponse delegate,
                int maxBytes
        ) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            return new BoundedInputStream(delegate.getBody(), maxBytes);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {

        private final int maxBytes;
        private int bytesRead;

        private BoundedInputStream(InputStream delegate, int maxBytes) {
            super(delegate);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                increment(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
                throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                increment(count);
            }
            return count;
        }

        private void increment(int count) throws IOException {
            if (bytesRead > maxBytes - count) {
                throw new IOException(
                        "web search provider response exceeds configured limit"
                );
            }
            bytesRead += count;
        }
    }
}
