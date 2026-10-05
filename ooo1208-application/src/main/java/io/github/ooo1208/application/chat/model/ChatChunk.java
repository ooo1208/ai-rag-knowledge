package io.github.ooo1208.application.chat.model;

/**
 * 统一的流式输出片段。
 * Provider 返回的不同格式会在 infrastructure 层转换成这个对象。
 */
public record ChatChunk (
        String content,
        String finishReason
) {
    public ChatChunk {
        if (content == null) {
            content = "";
        }
    }

    public boolean finished() {
        return finishReason != null;
    }
}
