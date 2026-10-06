package io.github.ooo1208.application.mcp.port.in;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.List;

/**
 * 查询某个模型配置当前允许选择的 MCP 工具。
 */
public interface ListModelToolsUseCase {

    List<McpToolDescriptor> list(ModelConfigId modelConfigId);
}
