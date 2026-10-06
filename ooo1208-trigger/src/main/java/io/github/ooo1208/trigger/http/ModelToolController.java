package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.mcp.exception.McpToolSelectionException;
import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.port.in.ExecuteMcpToolUseCase;
import io.github.ooo1208.application.mcp.port.in.ListModelToolsUseCase;
import io.github.ooo1208.application.mcp.port.in.SelectModelToolsUseCase;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型配置的 MCP 工具选择目录入口。
 *
 * <p>这里只返回已绑定且启用的安全摘要，不返回服务器地址、STDIO 命令或凭证。
 * 工具执行和联网能力必须继续经过服务端策略校验。</p>
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/model-configs")
public final class ModelToolController {

    private final ListModelToolsUseCase listModelToolsUseCase;
    private final SelectModelToolsUseCase selectModelToolsUseCase;
    private final ExecuteMcpToolUseCase executeMcpToolUseCase;

    public ModelToolController(
            ListModelToolsUseCase listModelToolsUseCase,
            SelectModelToolsUseCase selectModelToolsUseCase,
            ExecuteMcpToolUseCase executeMcpToolUseCase
    ) {
        this.listModelToolsUseCase = Objects.requireNonNull(
                listModelToolsUseCase
        );
        this.selectModelToolsUseCase = Objects.requireNonNull(
                selectModelToolsUseCase
        );
        this.executeMcpToolUseCase = Objects.requireNonNull(
                executeMcpToolUseCase
        );
    }

    @GetMapping("/{modelConfigId}/tools")
    public List<ModelToolResponse> listTools(
            @PathVariable String modelConfigId
    ) {
        return listModelToolsUseCase.list(new ModelConfigId(modelConfigId))
                .stream()
                .map(ModelToolResponse::from)
                .toList();
    }

    @PostMapping("/{modelConfigId}/tools/selection")
    public List<ModelToolResponse> selectTools(
            @PathVariable String modelConfigId,
            @RequestBody ModelToolSelectionRequest request
    ) {
        Objects.requireNonNull(request, "request");
        try {
            return selectModelToolsUseCase.select(
                            new ModelConfigId(modelConfigId),
                            request.toolIds()
                    )
                    .stream()
                    .map(ModelToolResponse::from)
                    .toList();
        } catch (McpToolSelectionException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }

    @PostMapping("/{modelConfigId}/tools/{toolId}/execute")
    public McpToolExecutionResponse executeTool(
            @PathVariable String modelConfigId,
            @PathVariable String toolId,
            @RequestBody(required = false) McpToolExecutionRequest request
    ) {
        Map<String, Object> arguments = request == null
                ? Map.of()
                : request.arguments();
        try {
            return McpToolExecutionResponse.from(
                    executeMcpToolUseCase.execute(
                            new ModelConfigId(modelConfigId),
                            toolId,
                            arguments
                    )
            );
        } catch (McpToolExecutionException exception) {
            throw new ResponseStatusException(
                    statusFor(exception.kind()),
                    exception.getMessage()
            );
        }
    }

    private static HttpStatus statusFor(
            McpToolExecutionException.Kind kind
    ) {
        return switch (kind) {
            case POLICY -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROVIDER -> HttpStatus.BAD_GATEWAY;
        };
    }

    /**
     * HTTP 层响应只保留前端选择器所需字段。
     */
    public record ModelToolResponse(
            String toolId,
            String serverId,
            String name,
            String displayName,
            String description,
            boolean readOnly,
            boolean requiresConfirmation,
            int maxCalls
    ) {

        private static ModelToolResponse from(McpToolDescriptor descriptor) {
            return new ModelToolResponse(
                    descriptor.toolId(),
                    descriptor.serverId(),
                    descriptor.name(),
                    descriptor.displayName(),
                    descriptor.description(),
                    descriptor.readOnly(),
                    descriptor.requiresConfirmation(),
                    descriptor.maxCalls()
            );
        }
    }
}
