package io.github.ooo1208.application.chat.port.in;

import io.github.ooo1208.application.chat.command.StreamChatCommand;
import io.github.ooo1208.application.chat.model.ChatChunk;
import org.reactivestreams.Publisher;

/**
 * 流式聊天 用例接口，定义处理聊天请求的业务逻辑。
 * 接收流式聊天命令
 * 持续返回聊天内容片段，直到聊天结束。
 * ----------------------------------
 * 用例：用户能完成的一个完整操作，例如发送聊天消息、接收模型回复、处理异常情况等。
 */
/**
 * 入站端口：执行一次流式聊天。
 * Publisher 是通用响应式协议，application 层不绑定 Reactor Flux。
 */
public interface StreamChatUseCase {

    Publisher<ChatChunk> stream(StreamChatCommand command);

}
