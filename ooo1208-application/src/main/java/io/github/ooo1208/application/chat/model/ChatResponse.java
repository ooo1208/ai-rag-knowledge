package io.github.ooo1208.application.chat.model;

/**
 * 非流式聊天的统一响应。
 * application 层只描述业务结果，不暴露 Spring AI 的 ChatResponse 类型。
 */
public record ChatResponse(
        String content,
        String finishReason,
        String modelConfigId
) {

    public ChatResponse(String content, String finishReason) {
        this(content, finishReason, null);
    }

    public ChatResponse {
        if (content == null) {
            content = "";
        }
    }

    public ChatResponse withModelConfigId(String value) {
        return new ChatResponse(content, finishReason, value);
    }
}
