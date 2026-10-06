package io.github.ooo1208.application.mcp.port.in;

import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.Map;

/**
 * 执行一个已经绑定到模型预设的 MCP 工具。
 */
public interface ExecuteMcpToolUseCase {

    McpToolExecutionResult execute(
            ModelConfigId modelConfigId,
            String toolId,
            Map<String, Object> arguments
    );
}
