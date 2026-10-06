package io.github.ooo1208.infrastructure.mcp;

import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.port.out.McpServerConnectionQueryPort;
import io.github.ooo1208.domain.mcp.McpTransportType;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 查询已登记 MCP 服务器连接的 JDBC 适配器。
 *
 * <p>只有启用且状态为 ACTIVE 的连接才能进入执行端口，调用方无法通过请求
 * 直接提交 endpoint 或 credentialRef。</p>
 */
@Component
@Profile("!in-memory-model-config")
public final class JdbcMcpServerConnectionQueryAdapter
        implements McpServerConnectionQueryPort {

    private static final String FIND_ENABLED_SERVER = """
            SELECT id, transport_type, endpoint_url, credential_ref
            FROM mcp_server_connection
            WHERE id = ?
              AND enabled = TRUE
              AND status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMcpServerConnectionQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public McpServerConnectionDescriptor queryEnabledServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        try {
            return jdbcTemplate.queryForObject(
                    FIND_ENABLED_SERVER,
                    (resultSet, rowNumber) ->
                            new McpServerConnectionDescriptor(
                                    resultSet.getString("id"),
                                    McpTransportType.valueOf(
                                            resultSet.getString(
                                                    "transport_type"
                                            )
                                    ),
                                    resultSet.getString("endpoint_url"),
                                    resultSet.getString("credential_ref")
                            ),
                    serverId.trim()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException(
                    "Unknown or disabled MCP server connection",
                    exception
            );
        }
    }
}
