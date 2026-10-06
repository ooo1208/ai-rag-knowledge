package io.github.ooo1208.application.mcp.exception;

import java.util.Objects;

/**
 * MCP 工具执行被策略拒绝或 Provider 执行失败。
 */
public final class McpToolExecutionException extends RuntimeException {

    public enum Kind {
        POLICY,
        UNAVAILABLE,
        PROVIDER
    }

    private final Kind kind;

    public McpToolExecutionException(String message) {
        this(message, Kind.POLICY);
    }

    public McpToolExecutionException(String message, Throwable cause) {
        this(message, cause, Kind.POLICY);
    }

    public McpToolExecutionException(String message, Kind kind) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public McpToolExecutionException(
            String message,
            Throwable cause,
            Kind kind
    ) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind kind() {
        return kind;
    }
}
