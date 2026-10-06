package io.github.ooo1208.application.modelcatalog.port.in;

import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;

import java.util.List;

/**
 * 查询当前调用方可见的启用模型配置。
 */
public interface ListModelConfigsUseCase {

    List<ModelConfigSummary> list();
}
