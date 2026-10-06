package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.port.in.ListModelToolsUseCase;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    public ModelToolController(ListModelToolsUseCase listModelToolsUseCase) {
        this.listModelToolsUseCase = Objects.requireNonNull(
                listModelToolsUseCase
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
