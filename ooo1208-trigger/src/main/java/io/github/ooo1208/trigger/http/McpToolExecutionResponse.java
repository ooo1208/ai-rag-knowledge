package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;

/**
 * MCP 工具输出摘要。content 始终来自外部系统，untrusted 固定为 true。
 */
public record McpToolExecutionResponse(
        String content,
        boolean providerError,
        boolean untrusted
) {

    public static McpToolExecutionResponse from(
            McpToolExecutionResult result
    ) {
        return new McpToolExecutionResponse(
                result.content(),
                result.providerError(),
                result.untrusted()
        );
    }
}
