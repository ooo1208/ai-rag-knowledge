package io.github.ooo1208.application.mcp.port.out;

import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;

/**
 * 查询已启用 MCP 服务器连接的出站端口。
 */
public interface McpServerConnectionQueryPort {

    McpServerConnectionDescriptor queryEnabledServer(String serverId);
}
