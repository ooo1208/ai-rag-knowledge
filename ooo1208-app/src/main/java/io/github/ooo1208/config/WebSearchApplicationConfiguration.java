package io.github.ooo1208.config;

import io.github.ooo1208.application.websearch.port.in.SearchWebUseCase;
import io.github.ooo1208.application.websearch.port.out.NetworkSearchPort;
import io.github.ooo1208.application.websearch.service.NetworkSearchApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Duration;

/**
 * 受控联网搜索的 application 组装配置。
 */
@Configuration
public class WebSearchApplicationConfiguration {

    @Bean
    public SearchWebUseCase searchWebUseCase(
            NetworkSearchPort networkSearchPort,
            @Value("${app.network-search.cache-ttl-ms:30000}") long cacheTtlMillis,
            @Value("${app.network-search.cache-max-entries:128}") int cacheMaxEntries
    ) {
        return new NetworkSearchApplicationService(
                networkSearchPort,
                Duration.ofMillis(cacheTtlMillis),
                cacheMaxEntries,
                Clock.systemUTC()
        );
    }
}
