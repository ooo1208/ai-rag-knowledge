package io.github.ooo1208.infrastructure.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AI 基础设施 Bean 配置。
 * 这里集中创建 Ollama、OpenAI、Embedding、文本切分器和 PgVector 相关组件。
 */
@Configuration
public class AiInfrastructureConfig {

    @Bean
    public OllamaApi ollamaApi(
            @Value("${spring.ai.ollama.base-url}") String baseUrl
    ) {
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    @Bean
    public SimpleVectorStore simpleVectorStore(
            @Value("${spring.ai.rag.embed}") String embeddingModel,
            OllamaApi ollamaApi,
            OpenAiEmbeddingModel openAiEmbeddingModel
    ) {
        if ("nomic-embed-text".equalsIgnoreCase(embeddingModel)) {
            OllamaEmbeddingModel ollamaEmbeddingModel =
                    OllamaEmbeddingModel.builder()
                            .ollamaApi(ollamaApi)
                            .defaultOptions(
                                    OllamaOptions.builder()
                                            .model(embeddingModel)
                                            .build()
                            )
                            .build();

            return SimpleVectorStore.builder(ollamaEmbeddingModel)
                    .build();
        }

        return SimpleVectorStore.builder(openAiEmbeddingModel)
                .build();
    }

    @Bean
    public PgVectorStore pgVectorStore(
            @Value("${spring.ai.rag.embed}") String embeddingModel,
            OllamaApi ollamaApi,
            OpenAiEmbeddingModel openAiEmbeddingModel,
            JdbcTemplate jdbcTemplate
    ) {
        if ("nomic-embed-text".equalsIgnoreCase(embeddingModel)) {
            OllamaEmbeddingModel ollamaEmbeddingModel =
                    OllamaEmbeddingModel.builder()
                            .ollamaApi(ollamaApi)
                            .defaultOptions(
                                    OllamaOptions.builder()
                                            .model(embeddingModel)
                                            .build()
                            )
                            .build();

            return PgVectorStore.builder(
                            jdbcTemplate,
                            ollamaEmbeddingModel
                    )
                    .build();
        }

        return PgVectorStore.builder(
                        jdbcTemplate,
                        openAiEmbeddingModel
                )
                .build();
    }
}
