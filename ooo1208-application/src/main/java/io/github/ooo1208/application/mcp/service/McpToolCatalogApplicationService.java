package io.github.ooo1208.application.mcp.service;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.port.in.ListModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.out.McpToolCatalogQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.List;
import java.util.Objects;

/**
 * MCP 工具选择目录用例。
 *
 * <p>这是第一阶段的只读能力：只返回服务端已经绑定到模型配置的工具，
 * 不接受请求方提交的任意 URL、命令或密钥。</p>
 */
public final class McpToolCatalogApplicationService
        implements ListModelToolsUseCase {

    private final McpToolCatalogQueryPort catalogQueryPort;

    public McpToolCatalogApplicationService(
            McpToolCatalogQueryPort catalogQueryPort
    ) {
        this.catalogQueryPort = Objects.requireNonNull(catalogQueryPort);
    }

    @Override
    public List<McpToolDescriptor> list(ModelConfigId modelConfigId) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        List<McpToolDescriptor> tools = catalogQueryPort.queryEnabledTools(
                modelConfigId
        );
        return tools == null ? List.of() : List.copyOf(tools);
    }
}
