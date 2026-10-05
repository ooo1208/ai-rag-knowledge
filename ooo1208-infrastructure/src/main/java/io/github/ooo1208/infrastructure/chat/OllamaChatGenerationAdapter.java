package io.github.ooo1208.infrastructure.chat;

import io.github.ooo1208.application.chat.model.ChatChunk;
import io.github.ooo1208.application.chat.model.ChatPrompt;
import io.github.ooo1208.application.chat.model.ChatResponse;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.port.out.ChatGenerationPort;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * Ollama 出站适配器。
 * 实现 application 的 ChatGenerationPort，把统一 ChatPrompt 转成 Ollama Prompt。
 */
@Component
public final class OllamaChatGenerationAdapter
        implements ChatGenerationPort {

    private final ChatModelFactory chatModelFactory;

    public OllamaChatGenerationAdapter(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory);
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.OLLAMA == providerType;
    }

    @Override
    public ChatResponse complete(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    ) {
        return SpringAiChatChunkMapper.toResponse(
                chatModelFactory
                        .createOllama(modelConfig)
                        .call(toSpringPrompt(prompt, modelConfig))
        );
    }

    @Override
    public Flux<ChatChunk> stream(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    ) {
        return chatModelFactory
                .createOllama(modelConfig)
                .stream(toSpringPrompt(prompt, modelConfig))
                .map(SpringAiChatChunkMapper::toChunk);
    }

    private Prompt toSpringPrompt(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    ) {
        return new Prompt(
                List.of(
                        new SystemMessage(prompt.systemPrompt()),
                        new UserMessage(prompt.userMessage())
                ),
                OllamaOptions.builder()
                        .model(modelConfig.upstreamModelId())
                        .temperature(modelConfig.temperature())
                        .numPredict(modelConfig.maxTokens())
                        .build()
        );
    }
}
