package io.github.ooo1208.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.StreamReadConstraints;
import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;
import io.github.ooo1208.application.mcp.port.out.McpToolExecutionPort;
import io.github.ooo1208.domain.mcp.McpTransportType;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import io.github.ooo1208.infrastructure.network.OutboundHostAllowlist;
import io.github.ooo1208.infrastructure.network.OutboundUrlValidator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.CookieHandler;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.time.Duration;
import java.net.http.WebSocket;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

/**
 * 基于 MCP Java SDK 的 SSE 工具执行适配器。
 *
 * <p>第一版默认关闭，只支持管理员登记的公网 SSE 连接；Streamable HTTP 和
 * STDIO 需要分别补 transport 与命令白名单后再开放。每次调用创建并关闭短生命周期
 * client，避免未完成连接缓存带来的资源泄漏。</p>
 */
@Component
public final class SpringAiMcpToolExecutionAdapter
        implements McpToolExecutionPort {

    private static final int MAX_OUTPUT_LENGTH = 20_000;
    private static final long MAX_SSE_EVENT_BYTES = 512 * 1024L;

    private final boolean enabled;
    private final int timeoutMillis;
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final OutboundHostAllowlist allowedHosts;

    public SpringAiMcpToolExecutionAdapter(
            @Value("${app.mcp.execution.enabled:false}") boolean enabled,
            @Value("${app.mcp.execution.timeout-ms:10000}") int timeoutMillis,
            CredentialResolver credentialResolver,
            ObjectMapper objectMapper,
            @Value("${app.mcp.execution.allowed-hosts:}") String allowedHosts
    ) {
        if (timeoutMillis < 100 || timeoutMillis > 30_000) {
            throw new IllegalArgumentException(
                    "MCP execution timeout must be between 100 and 30000 ms"
            );
        }
        this.enabled = enabled;
        this.timeoutMillis = timeoutMillis;
        this.credentialResolver = Objects.requireNonNull(credentialResolver);
        this.objectMapper = constrainedObjectMapper(objectMapper);
        this.allowedHosts = OutboundHostAllowlist.fromCsv(allowedHosts);
        if (enabled && this.allowedHosts.isEmpty()) {
            throw new IllegalArgumentException(
                    "MCP execution requires at least one allowed host"
            );
        }
    }

    @Override
    public McpToolExecutionResult execute(
            McpServerConnectionDescriptor server,
            String toolName,
            Map<String, Object> arguments
    ) {
        Objects.requireNonNull(server, "server");
        if (!enabled) {
            throw new McpToolExecutionException(
                    "MCP tool execution is disabled",
                    McpToolExecutionException.Kind.UNAVAILABLE
            );
        }
        if (server.transportType() != McpTransportType.SSE) {
            throw new McpToolExecutionException(
                    "MCP transport is not supported by the first execution adapter",
                    McpToolExecutionException.Kind.POLICY
            );
        }
        if (toolName == null || toolName.isBlank()) {
            throw new McpToolExecutionException(
                    "MCP tool name must not be blank"
            );
        }

        String apiKey = resolveCredential(server.credentialRef());
        OutboundUrlValidator.ValidatedUrl endpoint;
        try {
            endpoint = OutboundUrlValidator.validatePublicInternet(
                    server.endpointUrl()
            );
        } catch (IllegalArgumentException exception) {
            throw new McpToolExecutionException(
                    "MCP endpoint is not allowed by outbound policy",
                    exception,
                    McpToolExecutionException.Kind.POLICY
            );
        }
        if (!allowedHosts.matches(endpoint.uri().getHost())) {
            throw new McpToolExecutionException(
                    "MCP endpoint host is not in the configured allowlist",
                    McpToolExecutionException.Kind.POLICY
            );
        }

        HttpClientSseClientTransport.Builder transportBuilder =
                buildSseTransport(endpoint)
                        .clientBuilder(
                                new CappedHttpClientBuilder(
                                        HttpClient.newBuilder()
                                                .connectTimeout(
                                                        Duration.ofMillis(
                                                                timeoutMillis
                                                        )
                                                )
                                                .followRedirects(
                                                        HttpClient.Redirect.NEVER
                                                ),
                                        MAX_SSE_EVENT_BYTES
                                )
                        )
                        .objectMapper(objectMapper);
        if (apiKey != null) {
            transportBuilder.customizeRequest(
                    request -> request.header(
                            "Authorization",
                            "Bearer " + apiKey
                    )
            );
        }

        McpSyncClient client = null;
        try {
            client = McpClient.sync(transportBuilder.build())
                    .requestTimeout(Duration.ofMillis(timeoutMillis))
                    .initializationTimeout(Duration.ofMillis(timeoutMillis))
                    .clientInfo(new McpSchema.Implementation(
                            "ai-rag-knowledge",
                            "1.0"
                    ))
                    .build();
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(
                            toolName,
                            arguments == null ? Map.of() : arguments
                    )
            );
            return mapResult(result);
        } catch (McpToolExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new McpToolExecutionException(
                    "MCP provider tool execution failed",
                    exception,
                    McpToolExecutionException.Kind.PROVIDER
            );
        } finally {
            if (client != null) {
                try {
                    // 请求已经结束，立即关闭连接，避免 SDK 的固定 10 秒优雅关闭等待
                    // 把一次已超时的 HTTP 请求再次拖长。
                    client.close();
                } catch (RuntimeException ignored) {
                    // 原始执行结果/错误优先于关闭阶段的次生异常。
                }
            }
        }
    }

    private ObjectMapper constrainedObjectMapper(ObjectMapper source) {
        ObjectMapper copy = Objects.requireNonNull(source).copy();
        copy.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxNestingDepth(20)
                        .maxDocumentLength(512 * 1024L)
                        .maxStringLength(MAX_OUTPUT_LENGTH)
                        .maxNameLength(2_000)
                        .build()
        );
        return copy;
    }

    private HttpClientSseClientTransport.Builder buildSseTransport(
            OutboundUrlValidator.ValidatedUrl endpoint
    ) {
        URI endpointUri = endpoint.uri();
        String path = endpointUri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return HttpClientSseClientTransport.builder(
                            baseUri(endpointUri, "/")
                    )
                    .sseEndpoint("/sse");
        }

        int lastSlash = path.lastIndexOf('/');
        String parentPath = lastSlash < 0
                ? "/"
                : path.substring(0, lastSlash + 1);
        String ssePath = path.substring(lastSlash + 1);
        if (ssePath.isBlank()) {
            ssePath = "sse";
        }
        return HttpClientSseClientTransport.builder(
                        baseUri(endpointUri, parentPath)
                )
                .sseEndpoint(ssePath);
    }

    private String baseUri(URI endpointUri, String path) {
        try {
            return new URI(
                    endpointUri.getScheme(),
                    endpointUri.getRawAuthority(),
                    path,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException exception) {
            throw new McpToolExecutionException(
                    "MCP endpoint is not a valid URI",
                    exception,
                    McpToolExecutionException.Kind.POLICY
            );
        }
    }

    /**
     * MCP SDK 0.10.0 的 SSE parser 会在交给 Jackson 前拼接完整 event。
     * 这个 builder 在 HTTP client 层包一层订阅者，先限制原始 event 的大小，
     * 避免巨型单 event 绕过 JSON parser 的约束。
     */
    static final class CappedHttpClientBuilder
            implements HttpClient.Builder {

        private final HttpClient.Builder delegate;
        private final long maxEventBytes;

        CappedHttpClientBuilder(
                HttpClient.Builder delegate,
                long maxEventBytes
        ) {
            this.delegate = Objects.requireNonNull(delegate);
            this.maxEventBytes = maxEventBytes;
        }

        @Override
        public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
            delegate.cookieHandler(cookieHandler);
            return this;
        }

        @Override
        public HttpClient.Builder connectTimeout(Duration duration) {
            delegate.connectTimeout(duration);
            return this;
        }

        @Override
        public HttpClient.Builder sslContext(SSLContext sslContext) {
            delegate.sslContext(sslContext);
            return this;
        }

        @Override
        public HttpClient.Builder sslParameters(
                SSLParameters sslParameters
        ) {
            delegate.sslParameters(sslParameters);
            return this;
        }

        @Override
        public HttpClient.Builder executor(Executor executor) {
            delegate.executor(executor);
            return this;
        }

        @Override
        public HttpClient.Builder followRedirects(
                HttpClient.Redirect policy
        ) {
            delegate.followRedirects(policy);
            return this;
        }

        @Override
        public HttpClient.Builder version(HttpClient.Version version) {
            delegate.version(version);
            return this;
        }

        @Override
        public HttpClient.Builder priority(int priority) {
            delegate.priority(priority);
            return this;
        }

        @Override
        public HttpClient.Builder proxy(ProxySelector proxySelector) {
            delegate.proxy(proxySelector);
            return this;
        }

        @Override
        public HttpClient.Builder authenticator(Authenticator authenticator) {
            delegate.authenticator(authenticator);
            return this;
        }

        @Override
        public HttpClient.Builder localAddress(
                java.net.InetAddress localAddress
        ) {
            delegate.localAddress(localAddress);
            return this;
        }

        @Override
        public HttpClient build() {
            return new CappedHttpClient(delegate.build(), maxEventBytes);
        }
    }

    static final class CappedHttpClient extends HttpClient {

        private final HttpClient delegate;
        private final long maxEventBytes;

        CappedHttpClient(HttpClient delegate, long maxEventBytes) {
            this.delegate = Objects.requireNonNull(delegate);
            this.maxEventBytes = maxEventBytes;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return delegate.newWebSocketBuilder();
        }

        @Override
        public <T> HttpResponse<T> send(
                java.net.http.HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) throws IOException, InterruptedException {
            return delegate.send(
                    request,
                    cappedHandler(responseBodyHandler)
            );
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return delegate.sendAsync(
                    request,
                    cappedHandler(responseBodyHandler)
            );
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return delegate.sendAsync(
                    request,
                    cappedHandler(responseBodyHandler),
                    pushPromiseHandler
            );
        }

        @Override
        public void close() {
            delegate.close();
        }

        private <T> HttpResponse.BodyHandler<T> cappedHandler(
                HttpResponse.BodyHandler<T> handler
        ) {
            return responseInfo -> new CappedBodySubscriber<>(
                    handler.apply(responseInfo),
                    maxEventBytes
            );
        }
    }

    static final class CappedBodySubscriber<T>
            implements HttpResponse.BodySubscriber<T> {

        private final HttpResponse.BodySubscriber<T> delegate;
        private final long maxEventBytes;
        private long eventBytes;
        private boolean previousLineBreak;
        private boolean terminated;
        private Flow.Subscription subscription;

        CappedBodySubscriber(
                HttpResponse.BodySubscriber<T> delegate,
                long maxEventBytes
        ) {
            this.delegate = Objects.requireNonNull(delegate);
            this.maxEventBytes = maxEventBytes;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (terminated) {
                return;
            }
            try {
                for (ByteBuffer item : items) {
                    inspect(item);
                }
                delegate.onNext(items);
            } catch (RuntimeException exception) {
                terminated = true;
                if (subscription != null) {
                    subscription.cancel();
                }
                delegate.onError(exception);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!terminated) {
                terminated = true;
                delegate.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!terminated) {
                terminated = true;
                delegate.onComplete();
            }
        }

        @Override
        public CompletionStage<T> getBody() {
            return delegate.getBody();
        }

        private void inspect(ByteBuffer item) {
            ByteBuffer copy = item.asReadOnlyBuffer();
            while (copy.hasRemaining()) {
                byte value = copy.get();
                eventBytes++;
                if (eventBytes > maxEventBytes) {
                    throw new IllegalStateException(
                            "MCP SSE event exceeds configured limit"
                    );
                }
                if (value == '\n') {
                    if (previousLineBreak) {
                        eventBytes = 0;
                    }
                    previousLineBreak = true;
                } else if (value == '\r') {
                    // CRLF is counted as one logical line ending; the following
                    // LF decides whether two consecutive lines ended the event.
                } else {
                    previousLineBreak = false;
                }
            }
        }
    }

    private String resolveCredential(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return null;
        }
        try {
            return credentialResolver.resolve(credentialRef);
        } catch (IllegalArgumentException exception) {
            throw new McpToolExecutionException(
                    "MCP credentials are not configured",
                    exception,
                    McpToolExecutionException.Kind.UNAVAILABLE
            );
        }
    }

    private McpToolExecutionResult mapResult(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return new McpToolExecutionResult(
                    "",
                    result != null && Boolean.TRUE.equals(result.isError()),
                    true
            );
        }

        String content = result.content().stream()
                .filter(Objects::nonNull)
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        if (content.length() > MAX_OUTPUT_LENGTH) {
            content = content.substring(0, MAX_OUTPUT_LENGTH);
        }
        return new McpToolExecutionResult(
                content,
                Boolean.TRUE.equals(result.isError()),
                true
        );
    }
}
