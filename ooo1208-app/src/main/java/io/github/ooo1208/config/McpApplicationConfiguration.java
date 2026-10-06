package io.github.ooo1208.config;

import io.github.ooo1208.application.mcp.port.in.ListModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.out.McpToolCatalogQueryPort;
import io.github.ooo1208.application.mcp.service.McpToolCatalogApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具目录的 application 组装配置。
 */
@Configuration
public class McpApplicationConfiguration {

    @Bean
    public ListModelToolsUseCase listModelToolsUseCase(
            McpToolCatalogQueryPort catalogQueryPort
    ) {
        return new McpToolCatalogApplicationService(catalogQueryPort);
    }
}
