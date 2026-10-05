package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.chat.command.StreamChatCommand;
import io.github.ooo1208.application.chat.model.ChatChunk;
import io.github.ooo1208.application.chat.model.ChatResponse;
import io.github.ooo1208.application.chat.port.in.CompleteChatUseCase;
import io.github.ooo1208.application.chat.port.in.StreamChatUseCase;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 聊天 HTTP 入口适配器。
 * 只负责接收请求、转换命令和返回结果，不负责选择供应商或编排 RAG。
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final StreamChatUseCase streamChatUseCase;
    private final CompleteChatUseCase completeChatUseCase;

    public ChatController(
            StreamChatUseCase streamChatUseCase,
            CompleteChatUseCase completeChatUseCase
    ) {
        this.streamChatUseCase = Objects.requireNonNull(streamChatUseCase);
        this.completeChatUseCase = Objects.requireNonNull(completeChatUseCase);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ChatResponse complete(
            @RequestBody ChatRequest request
    ) {
        return completeChatUseCase.complete(toCommand(request));
    }

    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ChatChunk> stream(
            @RequestBody ChatRequest request
    ) {
        return Flux.from(streamChatUseCase.stream(toCommand(request)));
    }

    private StreamChatCommand toCommand(ChatRequest request) {
        return new StreamChatCommand(
                new ModelConfigId(request.modelConfigId()),
                request.message(),
                request.ragTag()
        );
    }
}
