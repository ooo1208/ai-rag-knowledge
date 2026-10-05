package io.github.ooo1208.application.chat.model;

/**
 * 发给模型的统一提示词。
 * PromptAssembler 负责创建它，模型适配器只负责把它转换成上游 SDK 的格式。
 */
public record ChatPrompt(
        String systemPrompt,
        String userMessage
) {
    public ChatPrompt {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        userMessage = userMessage.trim();
    }
}
