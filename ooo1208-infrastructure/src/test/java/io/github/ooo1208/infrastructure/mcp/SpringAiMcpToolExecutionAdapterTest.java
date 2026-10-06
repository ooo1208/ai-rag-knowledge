package io.github.ooo1208.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.domain.mcp.McpTransportType;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP SSE 适配器在真正连接 Provider 前的安全策略测试。
 */
class SpringAiMcpToolExecutionAdapterTest {

    @Test
    void disabledAdapterRejectsBeforeAnyOutboundValidation() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(false);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-web",
                                McpTransportType.SSE,
                                "http://127.0.0.1:8080/sse",
                                null
                        ),
                        "web_search",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.UNAVAILABLE,
                exception.kind()
        );
    }

    @Test
    void firstAdapterRejectsUnsupportedTransport() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(true);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-stdio",
                                McpTransportType.STDIO,
                                null,
                                null
                        ),
                        "read_file",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.POLICY,
                exception.kind()
        );
    }

    @Test
    void enabledAdapterRejectsPrivateEndpointBeforeConnecting() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(true);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-private",
                                McpTransportType.SSE,
                                "http://127.0.0.1:8080/sse",
                                null
                        ),
                        "web_search",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.POLICY,
                exception.kind()
        );
    }

    @Test
    void enabledAdapterRejectsMissingCredentialBeforeConnecting() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(true);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-public",
                                McpTransportType.SSE,
                                "https://example.com/mcp",
                                "config:missing.mcp.key"
                        ),
                        "web_search",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.UNAVAILABLE,
                exception.kind()
        );
    }

    @Test
    void enabledAdapterRequiresHostAllowlistAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpringAiMcpToolExecutionAdapter(
                        true,
                        1_000,
                        new CredentialResolver(new MockEnvironment()),
                        new ObjectMapper(),
                        ""
                )
        );
    }

    private SpringAiMcpToolExecutionAdapter newAdapter(boolean enabled) {
        return new SpringAiMcpToolExecutionAdapter(
                enabled,
                1_000,
                new CredentialResolver(new MockEnvironment()),
                new ObjectMapper(),
                "example.com"
        );
    }
}
