package io.github.ooo1208.infrastructure.modelcatalog;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.port.out.ModelConfigQueryPort;
import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;
import io.github.ooo1208.application.modelcatalog.port.out.ModelConfigCatalogQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * Phase 0 的模型配置查询适配器。
 * 只在显式 profile 下提供两个系统预置配置，用于本地离线验证调用链。
 */
@Component
@Profile("in-memory-model-config")
public final class InMemoryModelConfigQueryAdapter
        implements ModelConfigQueryPort, ModelConfigCatalogQueryPort {

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

    @Override
    public List<ModelConfigSummary> queryEnabledModelConfigs() {
        return List.of(
                new ModelConfigSummary(
                        new ModelConfigId("ollama-local"),
                        "Ollama Local",
                        ProviderType.OLLAMA,
                        "TEXT,RAG",
                        true
                ),
                new ModelConfigSummary(
                        new ModelConfigId("openai-compatible"),
                        "OpenAI Compatible",
                        ProviderType.OPENAI_COMPATIBLE,
                        "TEXT,RAG",
                        true
                )
        );
    }
}
