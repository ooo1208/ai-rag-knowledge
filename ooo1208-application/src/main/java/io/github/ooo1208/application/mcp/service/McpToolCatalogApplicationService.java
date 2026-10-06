package io.github.ooo1208.application.mcp.service;

import io.github.ooo1208.application.mcp.exception.McpToolSelectionException;
import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.port.in.ListModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.in.SelectModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.out.McpToolCatalogQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MCP 工具选择目录用例。
 *
 * <p>这是第一阶段的只读能力：只返回服务端已经绑定到模型配置的工具，
 * 不接受请求方提交的任意 URL、命令或密钥。</p>
 */
public final class McpToolCatalogApplicationService
        implements ListModelToolsUseCase, SelectModelToolsUseCase {

    private static final int MAX_SELECTED_TOOLS = 8;

    private final McpToolCatalogQueryPort catalogQueryPort;

    public McpToolCatalogApplicationService(
            McpToolCatalogQueryPort catalogQueryPort
    ) {
        this.catalogQueryPort = Objects.requireNonNull(catalogQueryPort);
    }

    @Override
    public List<McpToolDescriptor> list(ModelConfigId modelConfigId) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        List<McpToolDescriptor> tools = catalogQueryPort.queryEnabledTools(
                modelConfigId
        );
        return tools == null ? List.of() : List.copyOf(tools);
    }

    @Override
    public List<McpToolDescriptor> select(
            ModelConfigId modelConfigId,
            Collection<String> toolIds
    ) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        if (toolIds.size() > MAX_SELECTED_TOOLS) {
            throw new McpToolSelectionException(
                    "at most " + MAX_SELECTED_TOOLS
                            + " MCP tools can be selected"
            );
        }

        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        for (String toolId : toolIds) {
            if (toolId == null || toolId.isBlank()) {
                throw new McpToolSelectionException(
                        "selected MCP tool id must not be blank"
                );
            }
            String normalizedId = toolId.trim();
            if (normalizedId.length() > 100
                    || normalizedId.chars().anyMatch(Character::isISOControl)) {
                throw new McpToolSelectionException(
                        "selected MCP tool id is invalid"
                );
            }
            if (!normalizedIds.add(normalizedId)) {
                throw new McpToolSelectionException(
                        "selected MCP tool ids must be unique"
                );
            }
        }

        Map<String, McpToolDescriptor> enabledTools = list(modelConfigId)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        McpToolDescriptor::toolId,
                        Function.identity()
                ));

        return normalizedIds.stream()
                .map(toolId -> enabledTools.get(toolId))
                .map(tool -> {
                    if (tool == null) {
                        throw new McpToolSelectionException(
                                "selected MCP tool is not enabled for the model configuration"
                        );
                    }
                    return tool;
                })
                .toList();
    }
}
