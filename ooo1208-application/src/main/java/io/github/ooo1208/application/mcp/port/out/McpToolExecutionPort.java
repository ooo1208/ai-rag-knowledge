package io.github.ooo1208.application.mcp.port.out;

import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;

import java.util.Map;

/**
 * MCP 工具执行出站端口。
 *
 * <p>具体传输、SDK 和认证都留在 infrastructure；application 只传递已经由
 * 工具目录校验过的连接描述、工具名和受控参数。</p>
 */
public interface McpToolExecutionPort {

    McpToolExecutionResult execute(
            McpServerConnectionDescriptor server,
            String toolName,
            Map<String, Object> arguments
    );
}
