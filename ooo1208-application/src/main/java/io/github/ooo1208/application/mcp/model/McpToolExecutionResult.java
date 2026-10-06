package io.github.ooo1208.application.mcp.model;

/**
 * MCP 工具执行结果摘要。
 *
 * <p>工具输出来自外部系统，始终标记为不可信内容；调用方不能把它当作系统
 * 指令或未经审查的下一次工具参数。</p>
 */
public record McpToolExecutionResult(
        String content,
        boolean providerError,
        boolean untrusted
) {

    public McpToolExecutionResult {
        if (content == null) {
            content = "";
        }
        if (!untrusted) {
            throw new IllegalArgumentException(
                    "MCP tool output must be marked untrusted"
            );
        }
    }
}
