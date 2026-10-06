package io.github.ooo1208.application.modelcatalog.service;

import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;
import io.github.ooo1208.application.modelcatalog.port.in.ListModelConfigsUseCase;
import io.github.ooo1208.application.modelcatalog.port.out.ModelConfigCatalogQueryPort;

import java.util.List;
import java.util.Objects;

/**
 * 模型选择器目录用例。
 */
public final class ModelConfigCatalogApplicationService
        implements ListModelConfigsUseCase {

    private final ModelConfigCatalogQueryPort queryPort;

    public ModelConfigCatalogApplicationService(
            ModelConfigCatalogQueryPort queryPort
    ) {
        this.queryPort = Objects.requireNonNull(queryPort);
    }

    @Override
    public List<ModelConfigSummary> list() {
        List<ModelConfigSummary> summaries = queryPort.queryEnabledModelConfigs();
        return summaries == null ? List.of() : List.copyOf(summaries);
    }
}
