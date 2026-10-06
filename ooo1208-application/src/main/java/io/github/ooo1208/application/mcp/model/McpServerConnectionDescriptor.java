package io.github.ooo1208.application.mcp.model;

import io.github.ooo1208.domain.mcp.McpTransportType;

/**
 * MCP 服务器连接的内部调用描述。
 *
 * <p>该对象只在 application 到 infrastructure 的出站端口之间流转，不作为
 * HTTP 响应返回；endpoint 和 credentialRef 不得进入工具选择器 DTO。</p>
 */
public record McpServerConnectionDescriptor(
        String serverId,
        McpTransportType transportType,
        String endpointUrl,
        String credentialRef
) {

    public McpServerConnectionDescriptor {
        serverId = requireText(serverId, "serverId");
        if (transportType == null) {
            throw new IllegalArgumentException("transportType must not be null");
        }
        if (transportType == McpTransportType.STDIO) {
            if (endpointUrl != null && !endpointUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "STDIO connection must not have endpointUrl"
                );
            }
        } else {
            endpointUrl = requireText(endpointUrl, "endpointUrl");
        }
        credentialRef = credentialRef == null || credentialRef.isBlank()
                ? null
                : credentialRef.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
