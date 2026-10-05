package io.github.ooo1208.trigger.http;

/**
 * 聊天 HTTP 请求 DTO。
 * API Key、Base URL 和具体模型客户端都不允许从这里进入业务层。
 */
public record ChatRequest(
        String modelConfigId,
        String message,
        String ragTag
) {

    public ChatRequest {
        if (modelConfigId == null || modelConfigId.isBlank()) {
            throw new IllegalArgumentException(
                    "modelConfigId must not be blank"
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "message must not be blank"
            );
        }

        modelConfigId = modelConfigId.trim();
        message = message.trim();

        if (ragTag != null) {
            ragTag = ragTag.trim();
            if (ragTag.isBlank()) {
                ragTag = null;
            }
        }
    }
}
