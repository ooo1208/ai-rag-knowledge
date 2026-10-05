package io.github.ooo1208.application.chat.port.in;

import io.github.ooo1208.application.chat.command.StreamChatCommand;
import io.github.ooo1208.application.chat.model.ChatResponse;

/**
 * 入站端口：执行一次非流式聊天。
 * Controller 依赖这个接口，不依赖具体应用服务类。
 */
public interface CompleteChatUseCase {

    ChatResponse complete(StreamChatCommand command);
}
