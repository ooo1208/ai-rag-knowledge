package io.github.ooo1208.application.chat.port.out;

import io.github.ooo1208.application.chat.model.ChatChunk;
import io.github.ooo1208.application.chat.model.ChatPrompt;
import io.github.ooo1208.application.chat.model.ChatResponse;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.reactivestreams.Publisher;

/**
 * 出站端口：调用一个模型供应商并转换成统一聊天结果。
 * Ollama、OpenAI Compatible 等实现都放在 infrastructure 层。
 */
public interface ChatGenerationPort {

    boolean supports(ProviderType providerType);

    ChatResponse complete(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    );

    Publisher<ChatChunk> stream(
            ChatPrompt prompt,
            ResolvedModelConfig modelConfig
    );
}
