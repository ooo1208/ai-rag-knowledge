package io.github.ooo1208.infrastructure.chat;

import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import io.github.ooo1208.infrastructure.network.ModelProviderEndpointValidator;
import io.github.ooo1208.infrastructure.network.OutboundHostAllowlist;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatModelFactoryTest {

    private final ModelProviderEndpointValidator endpointValidator =
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

    private final ChatModelFactory factory = new ChatModelFactory(
            new CredentialResolver(
                    new MockEnvironment().withProperty(
                            "missing.key",
                            "test-key"
                    )
            ),
            endpointValidator
    );

    @Test
    void createsOllamaClientOnlyAfterEndpointValidation() {
        OllamaClientConfig config = new OllamaClientConfig();

        assertNotNull(factory.createOllama(config.modelConfig()));
    }

    @Test
    void rejectsProviderHostBeforeResolvingCredential() {
        ResolvedModelConfig config = new ResolvedModelConfig(
                new ModelConfigId("openai-private"),
                ProviderType.OPENAI_COMPATIBLE,
                "https://127.0.0.1:443/v1",
                "gpt-test",
                "config:missing.key",
                0.2,
                128,
                "",
                false
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createOpenAiCompatible(config)
        );
    }

    @Test
    void acceptsPublicOpenAiCompatibleEndpointWithoutCallingIt() {
        ResolvedModelConfig config = new ResolvedModelConfig(
                new ModelConfigId("openai-public"),
                ProviderType.OPENAI_COMPATIBLE,
                "https://1.1.1.1/v1",
                "gpt-test",
                "config:missing.key",
                0.2,
                128,
                "",
                false
        );

        assertDoesNotThrow(
                () -> assertNotNull(factory.createOpenAiCompatible(config))
        );
    }

    private record OllamaClientConfig() {
        private ResolvedModelConfig modelConfig() {
            return new ResolvedModelConfig(
                    new ModelConfigId("ollama-local"),
                    ProviderType.OLLAMA,
                    "http://127.0.0.1:11434",
                    "llama3",
                    null,
                    0.2,
                    128,
                    "",
                    false
            );
        }
    }
}
