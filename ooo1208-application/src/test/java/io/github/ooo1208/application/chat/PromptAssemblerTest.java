package io.github.ooo1208.application.chat;

import io.github.ooo1208.application.chat.model.ChatPrompt;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.service.PromptAssembler;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证联网结果进入 Prompt 时保留不可信上下文边界。
 */
class PromptAssemblerTest {

    @Test
    void webSearchResultsAreWrappedAsUntrustedContext() {
        PromptAssembler assembler = new PromptAssembler();
        ChatPrompt prompt = assembler.assemble(
                "今天有什么新闻？",
                modelConfig(),
                List.of(),
                List.of(new NetworkSearchResult(
                        "Example",
                        "https://example.com/news",
                        "外部摘要",
                        true
                ))
        );

        assertTrue(prompt.userMessage().contains("<web-search-results>"));
        assertTrue(prompt.userMessage().contains("外部不可信来源"));
        assertTrue(prompt.userMessage().contains("https://example.com/news"));
    }

    private ResolvedModelConfig modelConfig() {
        return new ResolvedModelConfig(
                new ModelConfigId("demo-model"),
                ProviderType.OLLAMA,
                "http://localhost:11434",
                "demo-model",
                null,
                0.7,
                256,
                "",
                true
        );
    }
}
