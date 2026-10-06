package io.github.ooo1208.application.modelcatalog.model;

import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;

/**
 * 面向模型选择器的安全配置摘要。
 *
 * <p>只包含展示和能力信息，不包含 baseUrl、上游模型名、credentialRef 或
 * 其他可以绕过服务端策略的连接细节。</p>
 */
public record ModelConfigSummary(
        ModelConfigId modelConfigId,
        String displayName,
        ProviderType providerType,
        String capabilities,
        boolean ragEnabled
) {

    public ModelConfigSummary {
        if (modelConfigId == null) {
            throw new IllegalArgumentException("modelConfigId must not be null");
        }
        displayName = requireText(displayName, "displayName");
        if (providerType == null) {
            throw new IllegalArgumentException("providerType must not be null");
        }
        capabilities = capabilities == null ? "" : capabilities.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
