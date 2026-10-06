package io.github.ooo1208.infrastructure.network;

import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelProviderEndpointValidatorTest {

    private final ModelProviderEndpointValidator validator =
            new ModelProviderEndpointValidator(
                    new ModelProviderEndpointValidator.EndpointPolicy(
                            OutboundHostAllowlist.of("127.0.0.1"),
                            Set.of(11_434),
                            true
                    ),
                    new ModelProviderEndpointValidator.EndpointPolicy(
                            OutboundHostAllowlist.of("1.1.1.1"),
                            Set.of(443),
                            false
                    )
            );

    @Test
    void ollamaAllowsOnlyConfiguredPrivateHostAndPort() {
        OutboundUrlValidator.ValidatedUrl validated = validator.validate(
                ProviderType.OLLAMA,
                "http://127.0.0.1:11434"
        );

        assertEquals("http://127.0.0.1:11434", validated.value());
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        ProviderType.OLLAMA,
                        "http://127.0.0.1:8080"
                )
        );
    }

    @Test
    void openAiCompatiblePolicyRejectsPrivateAddress() {
        OutboundUrlValidator.ValidatedUrl validated = validator.validate(
                ProviderType.OPENAI_COMPATIBLE,
                "https://1.1.1.1/v1"
        );

        assertEquals("https://1.1.1.1/v1", validated.value());
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        ProviderType.OPENAI_COMPATIBLE,
                        "https://127.0.0.1:443"
                )
        );
    }

    @Test
    void missingHostFailsClosedAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelProviderEndpointValidator(
                        new ModelProviderEndpointValidator.EndpointPolicy(
                                OutboundHostAllowlist.of(),
                                Set.of(11_434),
                                true
                        ),
                        new ModelProviderEndpointValidator.EndpointPolicy(
                                OutboundHostAllowlist.of("1.1.1.1"),
                                Set.of(443),
                                false
                        )
                )
        );
    }
}
