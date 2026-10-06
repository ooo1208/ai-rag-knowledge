package io.github.ooo1208.trigger.http;

import java.util.Map;

/**
 * MCP 工具执行参数。请求只提交工具参数，不允许覆盖服务器连接信息。
 */
public record McpToolExecutionRequest(Map<String, Object> arguments) {
}
