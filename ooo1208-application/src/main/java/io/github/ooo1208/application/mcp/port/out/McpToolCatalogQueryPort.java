package io.github.ooo1208.application.mcp.port.out;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.List;

/**
 * MCP 工具目录查询出站端口。
 *
 * <p>基础设施实现负责从数据库或其他目录读取白名单；application 层不依赖
 * MCP SDK、HTTP 地址或凭证。</p>
 */
public interface McpToolCatalogQueryPort {

    List<McpToolDescriptor> queryEnabledTools(ModelConfigId modelConfigId);
}
