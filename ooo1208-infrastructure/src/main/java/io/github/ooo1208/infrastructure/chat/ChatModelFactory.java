package io.github.ooo1208.infrastructure.chat;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import io.github.ooo1208.infrastructure.config.OpenAiApiSupport;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
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

    private final CredentialResolver credentialResolver;

    public ChatModelFactory(CredentialResolver credentialResolver) {
        this.credentialResolver = Objects.requireNonNull(credentialResolver);
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

        String apiKey = credentialResolver.resolve(modelConfig.credentialRef());
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApiSupport.create(modelConfig.baseUrl(), apiKey))
                .build();
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
