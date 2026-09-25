package io.github.ooo1208.api;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public interface IAiService {

    // 生成单条消息
    ChatResponse generate(String model, String message);

    // 生成流式消息
    Flux<ChatResponse> generateStream(String model, String message);

    // 生成流式消息，包含RAG标签
    Flux<ChatResponse> generateStreamRag(String model, String ragTag, String message);
}