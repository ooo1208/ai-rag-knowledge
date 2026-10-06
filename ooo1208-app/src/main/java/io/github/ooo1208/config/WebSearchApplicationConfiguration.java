package io.github.ooo1208.config;

import io.github.ooo1208.application.websearch.port.in.SearchWebUseCase;
import io.github.ooo1208.application.websearch.port.out.NetworkSearchPort;
import io.github.ooo1208.application.websearch.service.NetworkSearchApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 受控联网搜索的 application 组装配置。
 */
@Configuration
public class WebSearchApplicationConfiguration {

    @Bean
    public SearchWebUseCase searchWebUseCase(
            NetworkSearchPort networkSearchPort
    ) {
        return new NetworkSearchApplicationService(networkSearchPort);
    }
}
