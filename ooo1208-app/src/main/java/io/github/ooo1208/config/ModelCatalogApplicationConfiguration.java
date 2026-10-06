package io.github.ooo1208.config;

import io.github.ooo1208.application.modelcatalog.port.in.ListModelConfigsUseCase;
import io.github.ooo1208.application.modelcatalog.port.out.ModelConfigCatalogQueryPort;
import io.github.ooo1208.application.modelcatalog.service.ModelConfigCatalogApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型选择器目录的 application 组装配置。
 */
@Configuration
public class ModelCatalogApplicationConfiguration {

    @Bean
    public ListModelConfigsUseCase listModelConfigsUseCase(
            ModelConfigCatalogQueryPort queryPort
    ) {
        return new ModelConfigCatalogApplicationService(queryPort);
    }
}
