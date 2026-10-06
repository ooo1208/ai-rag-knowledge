package io.github.ooo1208.config;

import io.github.ooo1208.application.mcp.port.in.ListModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.in.SelectModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.in.ExecuteMcpToolUseCase;
import io.github.ooo1208.application.mcp.port.out.McpToolCatalogQueryPort;
import io.github.ooo1208.application.mcp.port.out.McpServerConnectionQueryPort;
import io.github.ooo1208.application.mcp.port.out.McpToolExecutionPort;
import io.github.ooo1208.application.mcp.service.McpToolCatalogApplicationService;
import io.github.ooo1208.application.mcp.service.McpToolExecutionApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具目录的 application 组装配置。
 */
@Configuration
public class McpApplicationConfiguration {

    @Bean
    public McpToolCatalogApplicationService mcpToolCatalogApplicationService(
            McpToolCatalogQueryPort catalogQueryPort
    ) {
        return new McpToolCatalogApplicationService(catalogQueryPort);
    }

    @Bean
    public ListModelToolsUseCase listModelToolsUseCase(
            McpToolCatalogApplicationService service
    ) {
        return service;
    }

    @Bean
    public SelectModelToolsUseCase selectModelToolsUseCase(
            McpToolCatalogApplicationService service
    ) {
        return service;
    }

    @Bean
    public ExecuteMcpToolUseCase executeMcpToolUseCase(
            McpToolCatalogApplicationService catalogService,
            McpServerConnectionQueryPort serverQueryPort,
            McpToolExecutionPort executionPort
    ) {
        return new McpToolExecutionApplicationService(
                catalogService,
                serverQueryPort,
                executionPort
        );
    }
}
