package io.github.ooo1208.application.mcp.service;

import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;
import io.github.ooo1208.application.mcp.port.in.ExecuteMcpToolUseCase;
import io.github.ooo1208.application.mcp.port.out.McpServerConnectionQueryPort;
import io.github.ooo1208.application.mcp.port.out.McpToolExecutionPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 工具执行策略编排器。
 *
 * <p>执行前再次按模型预设白名单解析工具，避免调用方绕过选择接口直接提交
 * serverId、endpoint 或任意工具名。第一版只允许只读且无需确认的工具，写工具
 * 必须等审批/审计链路接入后再开放。</p>
 */
public final class McpToolExecutionApplicationService
        implements ExecuteMcpToolUseCase {

    private static final int MAX_ARGUMENTS = 32;
    private static final int MAX_ARGUMENT_KEY_LENGTH = 100;

    private final McpToolCatalogApplicationService catalogService;
    private final McpServerConnectionQueryPort serverQueryPort;
    private final McpToolExecutionPort executionPort;

    public McpToolExecutionApplicationService(
            McpToolCatalogApplicationService catalogService,
            McpServerConnectionQueryPort serverQueryPort,
            McpToolExecutionPort executionPort
    ) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.serverQueryPort = Objects.requireNonNull(serverQueryPort);
        this.executionPort = Objects.requireNonNull(executionPort);
    }

    @Override
    public McpToolExecutionResult execute(
            ModelConfigId modelConfigId,
            String toolId,
            Map<String, Object> arguments
    ) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        if (toolId == null || toolId.isBlank()) {
            throw new McpToolExecutionException(
                    "toolId must not be blank"
            );
        }

        McpToolDescriptor tool = resolveTool(
                modelConfigId,
                toolId.trim()
        );
        if (!tool.readOnly() || tool.requiresConfirmation()) {
            throw new McpToolExecutionException(
                    "MCP tool requires explicit confirmation and is not executable yet"
            );
        }

        Map<String, Object> safeArguments = validateArguments(arguments);
        McpServerConnectionDescriptor server =
                serverQueryPort.queryEnabledServer(tool.serverId());
        if (server == null) {
            throw new McpToolExecutionException(
                    "MCP server connection is not enabled"
            );
        }

        return executionPort.execute(server, tool.name(), safeArguments);
    }

    private McpToolDescriptor resolveTool(
            ModelConfigId modelConfigId,
            String toolId
    ) {
        List<McpToolDescriptor> selected;
        try {
            selected = catalogService.select(
                    modelConfigId,
                    List.of(toolId)
            );
        } catch (RuntimeException exception) {
            if (exception instanceof McpToolExecutionException) {
                throw (McpToolExecutionException) exception;
            }
            throw new McpToolExecutionException(
                    "MCP tool is not enabled for the model configuration",
                    exception
            );
        }

        if (selected.size() != 1) {
            throw new McpToolExecutionException(
                    "MCP tool is not enabled for the model configuration"
            );
        }
        return selected.get(0);
    }

    private Map<String, Object> validateArguments(
            Map<String, Object> arguments
    ) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        if (arguments.size() > MAX_ARGUMENTS) {
            throw new McpToolExecutionException(
                    "too many MCP tool arguments"
            );
        }
        for (String key : arguments.keySet()) {
            if (key == null || key.isBlank()
                    || key.length() > MAX_ARGUMENT_KEY_LENGTH
                    || key.chars().anyMatch(Character::isISOControl)) {
                throw new McpToolExecutionException(
                        "MCP tool argument name is invalid"
                );
            }
        }
        // 保留 JSON null 参数，同时切断调用方对出站请求参数的后续修改。
        return Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
