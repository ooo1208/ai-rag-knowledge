package io.github.ooo1208.application.mcp;

import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;
import io.github.ooo1208.application.mcp.service.McpToolCatalogApplicationService;
import io.github.ooo1208.application.mcp.service.McpToolExecutionApplicationService;
import io.github.ooo1208.domain.mcp.McpTransportType;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP 工具执行前的模型白名单、只读策略和参数边界测试。
 */
class McpToolExecutionApplicationServiceTest {

    private static final ModelConfigId MODEL_CONFIG_ID =
            new ModelConfigId("demo-model");

    private static final McpToolDescriptor READ_TOOL = new McpToolDescriptor(
            "tool-search",
            "server-web",
            "web_search",
            "Web search",
            "Search the web",
            true,
            false,
            3
    );

    private static final McpToolDescriptor WRITE_TOOL = new McpToolDescriptor(
            "tool-write",
            "server-web",
            "write_note",
            "Write note",
            "Write a note",
            false,
            true,
            1
    );

    @Test
    void executeDelegatesOnlySelectedReadOnlyTool() {
        AtomicReference<Map<String, Object>> receivedArguments =
                new AtomicReference<>();
        McpToolExecutionApplicationService service = newService(
                List.of(READ_TOOL),
                receivedArguments
        );

        McpToolExecutionResult result = service.execute(
                MODEL_CONFIG_ID,
                "tool-search",
                Map.of("query", "spring ai")
        );

        assertEquals("ok", result.content());
        assertEquals(Map.of("query", "spring ai"), receivedArguments.get());
    }

    @Test
    void executePreservesOptionalNullArgumentsInSafeCopy() {
        AtomicReference<Map<String, Object>> receivedArguments =
                new AtomicReference<>();
        McpToolExecutionApplicationService service = newService(
                List.of(READ_TOOL),
                receivedArguments
        );

        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        arguments.put("query", "spring ai");
        arguments.put("cursor", null);
        service.execute(
                MODEL_CONFIG_ID,
                READ_TOOL.toolId(),
                arguments
        );

        assertNull(receivedArguments.get().get("cursor"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> receivedArguments.get().put("new", "value")
        );
    }

    @Test
    void executeRejectsToolOutsideModelAllowlist() {
        McpToolExecutionApplicationService service = newService(
                List.of(READ_TOOL),
                new AtomicReference<>()
        );

        assertThrows(
                McpToolExecutionException.class,
                () -> service.execute(
                        MODEL_CONFIG_ID,
                        "unknown-tool",
                        Map.of()
                )
        );
    }

    @Test
    void executeRejectsWriteOrConfirmationToolBeforeProviderCall() {
        McpToolExecutionApplicationService service = newService(
                List.of(WRITE_TOOL),
                new AtomicReference<>()
        );

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> service.execute(
                        MODEL_CONFIG_ID,
                        WRITE_TOOL.toolId(),
                        Map.of()
                )
        );

        assertEquals(McpToolExecutionException.Kind.POLICY, exception.kind());
    }

    @Test
    void executeRejectsTooManyArguments() {
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 33; index++) {
            arguments.put("key-" + index, index);
        }
        McpToolExecutionApplicationService service = newService(
                List.of(READ_TOOL),
                new AtomicReference<>()
        );

        assertThrows(
                McpToolExecutionException.class,
                () -> service.execute(
                        MODEL_CONFIG_ID,
                        READ_TOOL.toolId(),
                        arguments
                )
        );
    }

    @Test
    void executeHonorsEffectivePerMinuteCallBudget() {
        McpToolDescriptor oneCallTool = new McpToolDescriptor(
                READ_TOOL.toolId(),
                READ_TOOL.serverId(),
                READ_TOOL.name(),
                READ_TOOL.displayName(),
                READ_TOOL.description(),
                true,
                false,
                1
        );
        AtomicInteger providerCalls = new AtomicInteger();
        McpToolCatalogApplicationService catalog =
                new McpToolCatalogApplicationService(ignored -> List.of(oneCallTool));
        McpToolExecutionApplicationService service =
                new McpToolExecutionApplicationService(
                        catalog,
                        ignored -> new McpServerConnectionDescriptor(
                                "server-web",
                                McpTransportType.SSE,
                                "https://example.com/sse",
                                null
                        ),
                        (server, toolName, arguments) -> {
                            providerCalls.incrementAndGet();
                            return new McpToolExecutionResult("ok", false, true);
                        }
                );

        service.execute(MODEL_CONFIG_ID, oneCallTool.toolId(), Map.of());
        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> service.execute(
                        MODEL_CONFIG_ID,
                        oneCallTool.toolId(),
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.RATE_LIMITED,
                exception.kind()
        );
        assertEquals(1, providerCalls.get());
    }

    @Test
    void executeRejectsOversizedStringArgument() {
        McpToolExecutionApplicationService service = newService(
                List.of(READ_TOOL),
                new AtomicReference<>()
        );

        assertThrows(
                McpToolExecutionException.class,
                () -> service.execute(
                        MODEL_CONFIG_ID,
                        READ_TOOL.toolId(),
                        Map.of("query", "x".repeat(8_193))
                )
        );
    }

    private McpToolExecutionApplicationService newService(
            List<McpToolDescriptor> tools,
            AtomicReference<Map<String, Object>> receivedArguments
    ) {
        McpToolCatalogApplicationService catalog =
                new McpToolCatalogApplicationService(ignored -> tools);
        return new McpToolExecutionApplicationService(
                catalog,
                ignored -> new McpServerConnectionDescriptor(
                        "server-web",
                        McpTransportType.SSE,
                        "https://example.com/sse",
                        null
                ),
                (server, toolName, arguments) -> {
                    receivedArguments.set(arguments);
                    return new McpToolExecutionResult("ok", false, true);
                }
        );
    }
}
