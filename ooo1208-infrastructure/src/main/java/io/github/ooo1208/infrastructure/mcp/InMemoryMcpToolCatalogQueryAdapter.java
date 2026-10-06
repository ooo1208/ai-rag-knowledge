package io.github.ooo1208.infrastructure.mcp;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.port.out.McpToolCatalogQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 离线 profile 的空 MCP 工具目录。
 *
 * <p>离线聊天可以继续启动，但不会假装存在可执行的外部工具。</p>
 */
@Component
@Profile("in-memory-model-config")
public final class InMemoryMcpToolCatalogQueryAdapter
        implements McpToolCatalogQueryPort {

    @Override
    public List<McpToolDescriptor> queryEnabledTools(ModelConfigId modelConfigId) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        return List.of();
    }
}
