package io.github.ooo1208.infrastructure.modelcatalog;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.port.out.ModelConfigQueryPort;
import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;
import io.github.ooo1208.application.modelcatalog.port.out.ModelConfigCatalogQueryPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL 模型目录查询适配器。
 *
 * <p>application 层只依赖 ModelConfigQueryPort，数据库表结构和 JDBC 查询都留在
 * infrastructure 层。只有启用的预设、模型绑定和服务商连接才允许进入聊天调用链。</p>
 */
@Component
@Profile("!in-memory-model-config")
public final class JdbcModelConfigQueryAdapter
        implements ModelConfigQueryPort, ModelConfigCatalogQueryPort {

    private static final String FIND_ENABLED_MODEL_CONFIG = """
            SELECT
                preset.id AS model_config_id,
                provider.provider_type,
                provider.base_url,
                provider.credential_ref,
                binding.upstream_model_id,
                preset.temperature,
                preset.max_tokens,
                preset.system_prompt,
                preset.rag_enabled
            FROM model_preset preset
            JOIN model_binding binding
              ON binding.id = preset.model_binding_id
            JOIN provider_connection provider
              ON provider.id = binding.connection_id
            WHERE preset.id = ?
              AND preset.enabled = TRUE
              AND binding.enabled = TRUE
              AND provider.enabled = TRUE
              AND provider.status = 'ACTIVE'
            """;

    private static final RowMapper<ResolvedModelConfig> MODEL_CONFIG_ROW_MAPPER =
            (resultSet, rowNumber) -> new ResolvedModelConfig(
                    new ModelConfigId(resultSet.getString("model_config_id")),
                    ProviderType.valueOf(
                            resultSet.getString("provider_type")
                    ),
                    resultSet.getString("base_url"),
                    resultSet.getString("upstream_model_id"),
                    resultSet.getString("credential_ref"),
                    resultSet.getDouble("temperature"),
                    resultSet.getInt("max_tokens"),
                    resultSet.getString("system_prompt"),
                    resultSet.getBoolean("rag_enabled")
            );

    private static final String FIND_ENABLED_MODEL_CONFIGS = """
            SELECT
                preset.id AS model_config_id,
                preset.name AS display_name,
                provider.provider_type,
                binding.capabilities,
                preset.rag_enabled
            FROM model_preset preset
            JOIN model_binding binding
              ON binding.id = preset.model_binding_id
            JOIN provider_connection provider
              ON provider.id = binding.connection_id
            WHERE preset.enabled = TRUE
              AND binding.enabled = TRUE
              AND provider.enabled = TRUE
              AND provider.status = 'ACTIVE'
            ORDER BY provider.name, preset.name, preset.id
            """;

    private static final RowMapper<ModelConfigSummary>
            MODEL_CONFIG_SUMMARY_ROW_MAPPER =
            (resultSet, rowNumber) -> new ModelConfigSummary(
                    new ModelConfigId(resultSet.getString("model_config_id")),
                    resultSet.getString("display_name"),
                    ProviderType.valueOf(
                            resultSet.getString("provider_type")
                    ),
                    resultSet.getString("capabilities"),
                    resultSet.getBoolean("rag_enabled")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcModelConfigQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public ResolvedModelConfig queryModelConfig(ModelConfigId modelConfigId) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");

        try {
            return jdbcTemplate.queryForObject(
                    FIND_ENABLED_MODEL_CONFIG,
                    MODEL_CONFIG_ROW_MAPPER,
                    modelConfigId.value()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException(
                    "Unknown or disabled modelConfigId: "
                            + modelConfigId.value(),
                    exception
            );
        }
    }

    @Override
    public List<ModelConfigSummary> queryEnabledModelConfigs() {
        return jdbcTemplate.query(
                FIND_ENABLED_MODEL_CONFIGS,
                MODEL_CONFIG_SUMMARY_ROW_MAPPER
        );
    }
}
