package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;
import io.github.ooo1208.application.modelcatalog.port.in.ListModelConfigsUseCase;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 模型选择器目录入口。
 *
 * <p>只返回可见的启用配置摘要，绝不返回 baseUrl、上游模型名或凭证引用。</p>
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/model-configs")
public final class ModelConfigController {

    private final ListModelConfigsUseCase listModelConfigsUseCase;

    public ModelConfigController(ListModelConfigsUseCase listModelConfigsUseCase) {
        this.listModelConfigsUseCase = Objects.requireNonNull(
                listModelConfigsUseCase
        );
    }

    @GetMapping
    public List<ModelConfigResponse> list() {
        return listModelConfigsUseCase.list()
                .stream()
                .map(ModelConfigResponse::from)
                .toList();
    }

    public record ModelConfigResponse(
            String modelConfigId,
            String displayName,
            ProviderType providerType,
            String capabilities,
            boolean ragEnabled
    ) {

        private static ModelConfigResponse from(ModelConfigSummary summary) {
            return new ModelConfigResponse(
                    summary.modelConfigId().value(),
                    summary.displayName(),
                    summary.providerType(),
                    summary.capabilities(),
                    summary.ragEnabled()
            );
        }
    }
}
