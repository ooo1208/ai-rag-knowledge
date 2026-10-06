package io.github.ooo1208.application.modelcatalog.port.out;

import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;

import java.util.List;

/**
 * 启用模型配置目录查询端口。
 */
public interface ModelConfigCatalogQueryPort {

    List<ModelConfigSummary> queryEnabledModelConfigs();
}
