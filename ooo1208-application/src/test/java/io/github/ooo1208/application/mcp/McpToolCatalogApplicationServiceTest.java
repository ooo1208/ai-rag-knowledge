package io.github.ooo1208.application.mcp;

import io.github.ooo1208.application.mcp.exception.McpToolSelectionException;
import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.service.McpToolCatalogApplicationService;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP 工具白名单选择的纯 application 单元测试。
 */
class McpToolCatalogApplicationServiceTest {

    private static final ModelConfigId MODEL_CONFIG_ID =
            new ModelConfigId("demo-model");

    private static final McpToolDescriptor SEARCH_TOOL =
            new McpToolDescriptor(
                    "tool-search",
                    "server-web",
                    "web_search",
                    "Web search",
                    "Search the web",
                    true,
                    false,
                    3
            );

    @Test
    void selectReturnsOnlyRequestedEnabledToolsInRequestOrder() {
        McpToolCatalogApplicationService service = newService();

        List<McpToolDescriptor> selected = service.select(
                MODEL_CONFIG_ID,
                List.of(" tool-search ")
        );

        assertEquals(List.of(SEARCH_TOOL), selected);
    }

    @Test
    void selectRejectsToolOutsidePresetAllowlist() {
        McpToolCatalogApplicationService service = newService();

        assertThrows(
                McpToolSelectionException.class,
                () -> service.select(
                        MODEL_CONFIG_ID,
                        List.of("unknown-tool")
                )
        );
    }

    @Test
    void selectRejectsDuplicateIds() {
        McpToolCatalogApplicationService service = newService();

        assertThrows(
                McpToolSelectionException.class,
                () -> service.select(
                        MODEL_CONFIG_ID,
                        List.of("tool-search", "tool-search")
                )
        );
    }

    private McpToolCatalogApplicationService newService() {
        return new McpToolCatalogApplicationService(
                ignored -> List.of(SEARCH_TOOL)
        );
    }
}
