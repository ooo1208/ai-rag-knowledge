package io.github.ooo1208.application.mcp.port.in;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.Collection;
import java.util.List;

/**
 * 校验并归一化模型配置的 MCP 工具选择。
 */
public interface SelectModelToolsUseCase {

    List<McpToolDescriptor> select(
            ModelConfigId modelConfigId,
            Collection<String> toolIds
    );
}
