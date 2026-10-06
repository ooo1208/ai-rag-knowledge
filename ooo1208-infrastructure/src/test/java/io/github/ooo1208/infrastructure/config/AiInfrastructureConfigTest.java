package io.github.ooo1208.infrastructure.config;

import io.github.ooo1208.infrastructure.network.ModelProviderEndpointValidator;
import io.github.ooo1208.infrastructure.network.OutboundHostAllowlist;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiInfrastructureConfigTest {

    private final AiInfrastructureConfig config = new AiInfrastructureConfig();
    private final ModelProviderEndpointValidator endpointValidator =
            new ModelProviderEndpointValidator(
                    new ModelProviderEndpointValidator.EndpointPolicy(
                            OutboundHostAllowlist.of("127.0.0.1"),
                            Set.of(11_434),
                            true
                    ),
                    new ModelProviderEndpointValidator.EndpointPolicy(
                            OutboundHostAllowlist.of("127.0.0.1"),
                            Set.of(443),
                            true
                    )
            );

    @Test
    void fixedOllamaEndpointIsValidatedBeforeCreatingApi() {
        assertNotNull(
                config.ollamaApi(
                        "http://127.0.0.1:11434",
                        endpointValidator
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> config.ollamaApi(
                        "http://127.0.0.1:8080",
                        endpointValidator
                )
        );
    }

    @Test
    void fixedOpenAiEndpointIsValidatedBeforeCreatingApi() {
        assertNotNull(
                config.openAiApi(
                        "https://127.0.0.1:443/v1",
                        "test-key",
                        endpointValidator
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> config.openAiApi(
                        "https://127.0.0.1:8443/v1",
                        "test-key",
                        endpointValidator
                )
        );
    }
}
