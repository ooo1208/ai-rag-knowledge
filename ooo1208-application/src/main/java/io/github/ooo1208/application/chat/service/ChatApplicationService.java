package io.github.ooo1208.application.chat.service;

import io.github.ooo1208.application.chat.command.StreamChatCommand;
import io.github.ooo1208.application.chat.model.ChatChunk;
import io.github.ooo1208.application.chat.model.ChatPrompt;
import io.github.ooo1208.application.chat.model.ChatResponse;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.model.RetrievedDocument;
import io.github.ooo1208.application.chat.port.in.CompleteChatUseCase;
import io.github.ooo1208.application.chat.port.in.StreamChatUseCase;
import io.github.ooo1208.application.chat.port.out.ChatGenerationPort;
import io.github.ooo1208.application.chat.port.out.ModelConfigQueryPort;
import io.github.ooo1208.application.chat.port.out.RagRetrieverPort;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Objects;

/**
 * 聊天用例编排器。
 * 负责配置解析、RAG 检索、Prompt 组装和 Provider 选择，不直接依赖任何模型 SDK。
 */
public final class ChatApplicationService
        implements StreamChatUseCase, CompleteChatUseCase {

    private static final int DEFAULT_RAG_TOP_K = 5;

    private final ModelConfigQueryPort modelConfigQueryPort;
    private final RagRetrieverPort ragRetrieverPort;
    private final PromptAssembler promptAssembler;
    private final List<ChatGenerationPort> chatGenerationPorts;

    public ChatApplicationService(
            ModelConfigQueryPort modelConfigQueryPort,
            RagRetrieverPort ragRetrieverPort,
            PromptAssembler promptAssembler,
            List<ChatGenerationPort> chatGenerationPorts
    ) {
        this.modelConfigQueryPort =
                Objects.requireNonNull(modelConfigQueryPort);
        this.ragRetrieverPort =
                Objects.requireNonNull(ragRetrieverPort);
        this.promptAssembler =
                Objects.requireNonNull(promptAssembler);
        this.chatGenerationPorts = List.copyOf(
                Objects.requireNonNull(chatGenerationPorts)
        );
    }

    @Override
    public Publisher<ChatChunk> stream(StreamChatCommand command) {
        PreparedChat preparedChat = prepare(command);
        ChatGenerationPort generationPort = resolveGenerationPort(
                preparedChat.modelConfig().providerType()
        );

        return generationPort.stream(
                preparedChat.prompt(),
                preparedChat.modelConfig()
        );
    }

    @Override
    public ChatResponse complete(StreamChatCommand command) {
        PreparedChat preparedChat = prepare(command);
        ChatGenerationPort generationPort = resolveGenerationPort(
                preparedChat.modelConfig().providerType()
        );

        ChatResponse response = generationPort.complete(
                preparedChat.prompt(),
                preparedChat.modelConfig()
        );

        return response.withModelConfigId(
                preparedChat.modelConfig().modelConfigId().value()
        );
    }

    private PreparedChat prepare(StreamChatCommand command) {
        Objects.requireNonNull(command, "command");

        ResolvedModelConfig modelConfig =
                modelConfigQueryPort.queryModelConfig(
                        command.modelConfigId()
                );

        List<RetrievedDocument> documents =
                retrieveDocuments(command, modelConfig);

        ChatPrompt prompt = promptAssembler.assemble(
                command.message(),
                modelConfig,
                documents
        );

        return new PreparedChat(prompt, modelConfig);
    }

    private ChatGenerationPort resolveGenerationPort(
            ProviderType providerType
    ) {
        return chatGenerationPorts.stream()
                .filter(port -> port.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No chat generation adapter configured for provider: "
                                + providerType
                ));
    }

    private List<RetrievedDocument> retrieveDocuments(
            StreamChatCommand command,
            ResolvedModelConfig modelConfig
    ) {
        if (command.ragTag() == null) {
            return List.of();
        }

        if (!modelConfig.ragEnabled()) {
            throw new IllegalArgumentException(
                    "RAG is disabled for the selected model configuration"
            );
        }

        return ragRetrieverPort.retrieve(
                command.message(),
                DEFAULT_RAG_TOP_K,
                command.ragTag()
        );
    }

    private record PreparedChat(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    ) {
    }
}
