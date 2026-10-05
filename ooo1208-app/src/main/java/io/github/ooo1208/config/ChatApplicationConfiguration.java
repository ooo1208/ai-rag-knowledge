package io.github.ooo1208.config;

import io.github.ooo1208.application.chat.port.out.ChatGenerationPort;
import io.github.ooo1208.application.chat.port.out.ModelConfigQueryPort;
import io.github.ooo1208.application.chat.port.out.RagRetrieverPort;
import io.github.ooo1208.application.chat.service.PromptAssembler;
import io.github.ooo1208.application.chat.service.ChatApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 聊天应用层装配配置。
 * 在 boot 层把 application 的端口实现组装成 ChatApplicationService。
 */
@Configuration
public class ChatApplicationConfiguration {

    @Bean
    public PromptAssembler promptAssembler() {
        return new PromptAssembler();
    }

    @Bean
    public ChatApplicationService chatApplicationService(
            ModelConfigQueryPort modelConfigQueryPort,
            RagRetrieverPort ragRetrieverPort,
            PromptAssembler promptAssembler,
            List<ChatGenerationPort> chatGenerationPorts
    ) {
        return new ChatApplicationService(
                modelConfigQueryPort,
                ragRetrieverPort,
                promptAssembler,
                chatGenerationPorts
        );
    }
}
