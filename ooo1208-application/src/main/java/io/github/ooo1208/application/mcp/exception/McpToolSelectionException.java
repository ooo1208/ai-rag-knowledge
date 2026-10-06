package io.github.ooo1208.application.mcp.exception;

/**
 * 请求选择的 MCP 工具不在模型预设白名单中。
 */
public final class McpToolSelectionException extends RuntimeException {

    public McpToolSelectionException(String message) {
        super(message);
    }
}
