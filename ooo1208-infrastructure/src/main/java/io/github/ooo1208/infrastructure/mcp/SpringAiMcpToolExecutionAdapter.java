package io.github.ooo1208.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;
import io.github.ooo1208.application.mcp.port.out.McpToolExecutionPort;
import io.github.ooo1208.domain.mcp.McpTransportType;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import io.github.ooo1208.infrastructure.network.OutboundUrlValidator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
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

    private final boolean enabled;
    private final int timeoutMillis;
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    public SpringAiMcpToolExecutionAdapter(
            @Value("${app.mcp.execution.enabled:false}") boolean enabled,
            @Value("${app.mcp.execution.timeout-ms:10000}") int timeoutMillis,
            CredentialResolver credentialResolver,
            ObjectMapper objectMapper
    ) {
        if (timeoutMillis < 100 || timeoutMillis > 30_000) {
            throw new IllegalArgumentException(
                    "MCP execution timeout must be between 100 and 30000 ms"
            );
        }
        this.enabled = enabled;
        this.timeoutMillis = timeoutMillis;
        this.credentialResolver = Objects.requireNonNull(credentialResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
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

        HttpClientSseClientTransport.Builder transportBuilder =
                HttpClientSseClientTransport.builder(endpoint.value())
                        .clientBuilder(
                                HttpClient.newBuilder()
                                        .connectTimeout(
                                                Duration.ofMillis(timeoutMillis)
                                        )
                                        .followRedirects(
                                                HttpClient.Redirect.NEVER
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
                    client.closeGracefully();
                } catch (RuntimeException ignored) {
                    // 原始执行结果/错误优先于关闭阶段的次生异常。
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
