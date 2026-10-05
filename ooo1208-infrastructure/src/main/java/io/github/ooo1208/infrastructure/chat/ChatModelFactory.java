package io.github.ooo1208.infrastructure.chat;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import io.github.ooo1208.infrastructure.config.OpenAiApiSupport;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 根据模型目录中的连接信息创建一次聊天客户端。
 *
 * <p>模型目录允许管理员修改服务地址和凭证引用，因此聊天适配器不能只依赖
 * 启动时创建的固定客户端。凭证本身仍由外部配置提供，数据库只保存引用。</p>
 */
@Component
public final class ChatModelFactory {

    private static final String CONFIG_CREDENTIAL_PREFIX = "config:";

    private final Environment environment;

    public ChatModelFactory(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    public OllamaChatModel createOllama(ResolvedModelConfig modelConfig) {
        requireProvider(modelConfig, ProviderType.OLLAMA);

        return OllamaChatModel.builder()
                .ollamaApi(
                        OllamaApi.builder()
                                .baseUrl(modelConfig.baseUrl())
                                .build()
                )
                .build();
    }

    public OpenAiChatModel createOpenAiCompatible(
            ResolvedModelConfig modelConfig
    ) {
        requireProvider(modelConfig, ProviderType.OPENAI_COMPATIBLE);

        String apiKey = resolveCredential(modelConfig.credentialRef());
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApiSupport.create(modelConfig.baseUrl(), apiKey))
                .build();
    }

    private String resolveCredential(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            throw new IllegalArgumentException(
                    "OpenAI-compatible model credentialRef must not be blank"
            );
        }

        String reference = credentialRef.trim();
        if (!reference.startsWith(CONFIG_CREDENTIAL_PREFIX)) {
            throw new IllegalArgumentException(
                    "Unsupported model credentialRef scheme"
            );
        }

        String propertyName = reference.substring(
                CONFIG_CREDENTIAL_PREFIX.length()
        ).trim();
        if (propertyName.isBlank()) {
            throw new IllegalArgumentException(
                    "Model credentialRef property must not be blank"
            );
        }

        String credential = environment.getProperty(propertyName);
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException(
                    "Model credential is not configured: " + propertyName
            );
        }
        return credential;
    }

    private void requireProvider(
            ResolvedModelConfig modelConfig,
            ProviderType expectedProvider
    ) {
        Objects.requireNonNull(modelConfig, "modelConfig");
        if (modelConfig.providerType() != expectedProvider) {
            throw new IllegalArgumentException(
                    "Expected provider " + expectedProvider
                            + " but got " + modelConfig.providerType()
            );
        }
    }
}
