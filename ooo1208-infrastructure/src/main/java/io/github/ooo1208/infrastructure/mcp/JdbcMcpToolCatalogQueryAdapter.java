package io.github.ooo1208.infrastructure.mcp;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.port.out.McpToolCatalogQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL MCP 工具白名单查询适配器。
 *
 * <p>查询同时检查模型预设、连接、服务器、工具和绑定关系的启用状态，
 * 因而客户端无法通过修改请求参数绕过服务端白名单。</p>
 */
@Component
@Profile("!in-memory-model-config")
public final class JdbcMcpToolCatalogQueryAdapter
        implements McpToolCatalogQueryPort {

    private static final String FIND_ENABLED_TOOLS = """
            SELECT
                tool.id AS tool_id,
                server.id AS server_id,
                tool.name,
                tool.display_name,
                tool.description,
                tool.read_only,
                (tool.requires_confirmation OR binding.requires_confirmation)
                    AS requires_confirmation,
                LEAST(tool.max_calls, binding.max_calls) AS max_calls
            FROM model_preset preset
            JOIN model_preset_tool binding
              ON binding.model_preset_id = preset.id
            JOIN mcp_tool tool
              ON tool.id = binding.mcp_tool_id
            JOIN mcp_server_connection server
              ON server.id = tool.server_connection_id
            WHERE preset.id = ?
              AND preset.enabled = TRUE
              AND binding.enabled = TRUE
              AND tool.enabled = TRUE
              AND server.enabled = TRUE
              AND server.status = 'ACTIVE'
            ORDER BY tool.display_name, tool.id
            """;

    private static final RowMapper<McpToolDescriptor> TOOL_ROW_MAPPER =
            (resultSet, rowNumber) -> new McpToolDescriptor(
                    resultSet.getString("tool_id"),
                    resultSet.getString("server_id"),
                    resultSet.getString("name"),
                    resultSet.getString("display_name"),
                    resultSet.getString("description"),
                    resultSet.getBoolean("read_only"),
                    resultSet.getBoolean("requires_confirmation"),
                    resultSet.getInt("max_calls")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcMcpToolCatalogQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public List<McpToolDescriptor> queryEnabledTools(ModelConfigId modelConfigId) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        return jdbcTemplate.query(
                FIND_ENABLED_TOOLS,
                TOOL_ROW_MAPPER,
                modelConfigId.value()
        );
    }
}
