package io.github.ooo1208.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 首次启动时写入两个系统预置模型配置。
 *
 * <p>这里只负责补齐空数据库中的系统默认值，使用 ON CONFLICT DO NOTHING
 * 保留管理员后续对连接、模型和预设的修改。</p>
 */
@Component
public final class ModelCatalogDataInitializer implements ApplicationRunner {

    private static final String INSERT_PROVIDER_CONNECTION = """
            INSERT INTO provider_connection
                (id, provider_type, name, base_url, credential_ref, enabled, status)
            VALUES (?, ?, ?, ?, ?, TRUE, 'ACTIVE')
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String INSERT_MODEL_BINDING = """
            INSERT INTO model_binding
                (id, connection_id, upstream_model_id, display_name,
                 capabilities, context_window, enabled, source)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, 'SYSTEM')
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String INSERT_MODEL_PRESET = """
            INSERT INTO model_preset
                (id, model_binding_id, name, temperature, max_tokens,
                 system_prompt, rag_enabled, enabled)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String ollamaBaseUrl;
    private final String openAiBaseUrl;
    private final String ollamaModel;
    private final String openAiModel;

    public ModelCatalogDataInitializer(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl,
            @Value("${spring.ai.openai.base-url}") String openAiBaseUrl,
            @Value("${app.chat.ollama.model:deepseek-r1:1.5b}") String ollamaModel,
            @Value("${app.chat.openai-compatible.model:gpt-4o-mini}") String openAiModel
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.ollamaBaseUrl = Objects.requireNonNull(ollamaBaseUrl);
        this.openAiBaseUrl = Objects.requireNonNull(openAiBaseUrl);
        this.ollamaModel = Objects.requireNonNull(ollamaModel);
        this.openAiModel = Objects.requireNonNull(openAiModel);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        insertProviderConnections();
        insertModelBindings();
        insertModelPresets();
    }

    private void insertProviderConnections() {
        jdbcTemplate.update(
                INSERT_PROVIDER_CONNECTION,
                "pc_ollama_local",
                "OLLAMA",
                "Ollama Local",
                ollamaBaseUrl,
                null
        );
        jdbcTemplate.update(
                INSERT_PROVIDER_CONNECTION,
                "pc_openai_compatible",
                "OPENAI_COMPATIBLE",
                "OpenAI Compatible",
                openAiBaseUrl,
                "config:spring.ai.openai.api-key"
        );
    }

    private void insertModelBindings() {
        jdbcTemplate.update(
                INSERT_MODEL_BINDING,
                "mb_ollama_local",
                "pc_ollama_local",
                ollamaModel,
                ollamaModel,
                "TEXT,RAG",
                null
        );
        jdbcTemplate.update(
                INSERT_MODEL_BINDING,
                "mb_openai_compatible",
                "pc_openai_compatible",
                openAiModel,
                openAiModel,
                "TEXT,RAG",
                null
        );
    }

    private void insertModelPresets() {
        jdbcTemplate.update(
                INSERT_MODEL_PRESET,
                "ollama-local",
                "mb_ollama_local",
                "Ollama Local",
                0.7,
                2048,
                ""
        );
        jdbcTemplate.update(
                INSERT_MODEL_PRESET,
                "openai-compatible",
                "mb_openai_compatible",
                "OpenAI Compatible",
                0.7,
                2048,
                ""
        );
    }
}
