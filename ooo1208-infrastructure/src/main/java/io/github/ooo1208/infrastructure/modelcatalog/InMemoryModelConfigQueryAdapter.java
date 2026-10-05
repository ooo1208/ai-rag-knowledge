package io.github.ooo1208.infrastructure.modelcatalog;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.port.out.ModelConfigQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Phase 0 的模型配置查询适配器。
 * 现在提供两个系统预置配置；后续替换为数据库适配器，不需要改 ChatApplicationService。
 */
@Component
public final class InMemoryModelConfigQueryAdapter
        implements ModelConfigQueryPort {

    private final Map<String, ResolvedModelConfig> modelConfigs;

    public InMemoryModelConfigQueryAdapter(
            @Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl,
            @Value("${spring.ai.openai.base-url}") String openAiBaseUrl,
            @Value("${app.chat.ollama.model:deepseek-r1:1.5b}") String ollamaModel,
            @Value("${app.chat.openai-compatible.model:gpt-4o-mini}") String openAiModel
    ) {
        this.modelConfigs = Map.of(
                "ollama-local",
                new ResolvedModelConfig(
                        new ModelConfigId("ollama-local"),
                        ProviderType.OLLAMA,
                        ollamaBaseUrl,
                        ollamaModel,
                        null,
                        0.7,
                        2048,
                        "",
                        true
                ),
                "openai-compatible",
                new ResolvedModelConfig(
                        new ModelConfigId("openai-compatible"),
                        ProviderType.OPENAI_COMPATIBLE,
                        openAiBaseUrl,
                        openAiModel,
                        "config:spring.ai.openai.api-key",
                        0.7,
                        2048,
                        "",
                        true
                )
        );
    }

    @Override
    public ResolvedModelConfig queryModelConfig(
            ModelConfigId modelConfigId
    ) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");

        ResolvedModelConfig modelConfig =
                modelConfigs.get(modelConfigId.value());

        if (modelConfig == null) {
            throw new IllegalArgumentException(
                    "Unknown modelConfigId: " + modelConfigId.value()
            );
        }

        return modelConfig;
    }
}
