package io.github.ooo1208.application.chat.command;


import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

/**
 * 应用层接收聊天请求的对象，包含模型配置ID、消息和RAG标签
 * CQRS模式中的命令对象，将聊天请求封装为一个对象，方便传递和处理。
 */
/**
 * 聊天用例的输入命令。
 * 来源可以是 HTTP、定时任务或测试，但 application 层不依赖这些入口技术。
 */
public record StreamChatCommand (
        ModelConfigId modelConfigId,
        String message,
        String ragTag,
        boolean webSearch
) {
    /**
     * 兼容没有联网搜索开关的旧调用方。
     */
    public StreamChatCommand(
            ModelConfigId modelConfigId,
            String message,
            String ragTag
    ) {
        this(modelConfigId, message, ragTag, false);
    }

    public StreamChatCommand {
        if (modelConfigId == null) {
            throw new IllegalArgumentException("modelConfigId must not be null");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        message = message.trim();

        if (ragTag != null) {
            ragTag = ragTag.trim();

            if (ragTag.isBlank()) {
                ragTag = null;
            }
        }
    }
}
