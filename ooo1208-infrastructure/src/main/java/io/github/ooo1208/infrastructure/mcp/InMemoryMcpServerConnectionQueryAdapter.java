package io.github.ooo1208.infrastructure.mcp;

import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.port.out.McpServerConnectionQueryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 离线 profile 不启用外部 MCP 连接。
 */
@Component
@Profile("in-memory-model-config")
public final class InMemoryMcpServerConnectionQueryAdapter
        implements McpServerConnectionQueryPort {

    @Override
    public McpServerConnectionDescriptor queryEnabledServer(String serverId) {
        Objects.requireNonNull(serverId, "serverId");
        throw new IllegalArgumentException(
                "MCP server connections are unavailable in offline profile"
        );
    }
}
