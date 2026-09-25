package io.github.ooo1208.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
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

@Configuration
public class OllamaConfig {

    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        return OllamaApi.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public OpenAiApi openAiApi(@Value("${spring.ai.openai.base-url}") String baseUrl, @Value("${spring.ai.openai.api-key}") String apikey) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apikey)
                .build();
    }


    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder().ollamaApi(ollamaApi).build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /**
     * 内存向量库 - 数据存在内存中(Map)，重启即丢失
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(@Value("${spring.ai.rag.embed}") String model, OllamaApi ollamaApi, OpenAiEmbeddingModel openAiEmbeddingModel) {
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(OllamaOptions.builder().model(model).build())
                    .build();
            return SimpleVectorStore.builder(embeddingModel).build();
        } else {
            return SimpleVectorStore.builder(openAiEmbeddingModel).build();
        }
    }

    /**
     * PgVector持久化向量库 - 数据存储在PostgreSQL(pgvector扩展)中，重启不丢失，用于生产环境
     */
    @Bean
    public PgVectorStore pgVectorStore(@Value("${spring.ai.rag.embed}") String model, OllamaApi ollamaApi, OpenAiEmbeddingModel openAiEmbeddingModel, JdbcTemplate jdbcTemplate) {
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(OllamaOptions.builder().model(model).build())
                    .build();
            return PgVectorStore.builder(jdbcTemplate,embeddingModel).build();
        } else {
            return PgVectorStore.builder(jdbcTemplate,openAiEmbeddingModel).build();
        }
    }

}
