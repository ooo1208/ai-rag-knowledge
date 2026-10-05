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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * OpenAI Compatible 出站适配器。
 * DeepSeek 等遵循 OpenAI 协议的服务可以复用这个实现。
 */
@Component
public final class OpenAiCompatibleChatGenerationAdapter
        implements ChatGenerationPort {

    private final ChatModelFactory chatModelFactory;

    public OpenAiCompatibleChatGenerationAdapter(
            ChatModelFactory chatModelFactory
    ) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory);
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.OPENAI_COMPATIBLE == providerType;
    }

    @Override
    public ChatResponse complete(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    ) {
        return SpringAiChatChunkMapper.toResponse(
                chatModelFactory
                        .createOpenAiCompatible(modelConfig)
                        .call(toSpringPrompt(prompt, modelConfig))
        );
    }

    @Override
    public Flux<ChatChunk> stream(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    ) {
        return chatModelFactory
                .createOpenAiCompatible(modelConfig)
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
                OpenAiChatOptions.builder()
                        .model(modelConfig.upstreamModelId())
                        .temperature(modelConfig.temperature())
                        .maxTokens(modelConfig.maxTokens())
                        .build()
        );
    }
}
