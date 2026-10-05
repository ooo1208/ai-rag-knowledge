package io.github.ooo1208.application.chat.port.out;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

/**
 * 出站端口：根据稳定的 modelConfigId 查询本次调用所需的完整配置。
 * 未来可以由内存、数据库或远程配置中心实现。
 */
public interface ModelConfigQueryPort {
    ResolvedModelConfig queryModelConfig(ModelConfigId modelConfigId);
}
