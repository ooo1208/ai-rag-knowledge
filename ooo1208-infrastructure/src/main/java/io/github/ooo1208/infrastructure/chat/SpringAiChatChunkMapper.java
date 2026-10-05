package io.github.ooo1208.infrastructure.chat;

import io.github.ooo1208.application.chat.model.ChatChunk;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Spring AI 响应转换器。
 * 只负责 SDK 类型和 application 类型之间的映射，不参与业务编排。
 */
final class SpringAiChatChunkMapper {

    private SpringAiChatChunkMapper() {
    }

    static ChatChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return new ChatChunk("", null);
        }

        Generation generation = response.getResult();
        String content = generation.getOutput() == null
                ? ""
                : generation.getOutput().getText();

        String finishReason = generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();

        return new ChatChunk(content, finishReason);
    }

    static io.github.ooo1208.application.chat.model.ChatResponse
    toResponse(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return new io.github.ooo1208.application.chat.model.ChatResponse(
                    "",
                    null
            );
        }

        Generation generation = response.getResult();
        String content = generation.getOutput() == null
                ? ""
                : generation.getOutput().getText();

        String finishReason = generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();

        return new io.github.ooo1208.application.chat.model.ChatResponse(
                content,
                finishReason
        );
    }
}
