package io.github.ooo1208.application.mcp.model;

/**
 * 可供某个模型配置选择的 MCP 工具摘要。
 *
 * <p>这里只暴露稳定标识和安全策略，不暴露 MCP 地址、命令或凭证引用。
 * 工具的实际调用由后续的执行端口负责，模型选择器只消费这个摘要。</p>
 */
public record McpToolDescriptor(
        String toolId,
        String serverId,
        String name,
        String displayName,
        String description,
        boolean readOnly,
        boolean requiresConfirmation,
        int maxCalls
) {

    public McpToolDescriptor {
        toolId = requireText(toolId, "toolId");
        serverId = requireText(serverId, "serverId");
        name = requireText(name, "name");
        displayName = requireText(displayName, "displayName");
        description = description == null ? "" : description.trim();
        if (maxCalls <= 0) {
            throw new IllegalArgumentException("maxCalls must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
