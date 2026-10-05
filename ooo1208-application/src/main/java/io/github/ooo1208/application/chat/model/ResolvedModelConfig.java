package io.github.ooo1208.application.chat.model;

import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;

/**
 * 本次请求解析后的模型调用信息。
 * 它是 ModelConfigQueryPort 的输出，只在一次调用链中使用，不直接返回给前端。
 */
public record ResolvedModelConfig(
        ModelConfigId modelConfigId,
        ProviderType providerType,
        String baseUrl,
        String upstreamModelId,
        String credentialRef,
        double temperature,
        int maxTokens,
        String systemPrompt,
        boolean ragEnabled
) {

    public ResolvedModelConfig {
        if (modelConfigId == null) {
            throw new IllegalArgumentException("modelConfigId must not be null");
        }

        if (providerType == null) {
            throw new IllegalArgumentException("providerType must not be null");
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }

        if (upstreamModelId == null || upstreamModelId.isBlank()) {
            throw new IllegalArgumentException("upstreamModelId must not be blank");
        }

        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be greater than zero");
        }

        baseUrl = baseUrl.trim();
        upstreamModelId = upstreamModelId.trim();

        if (systemPrompt == null) {
            systemPrompt = "";
        }
    }
}
