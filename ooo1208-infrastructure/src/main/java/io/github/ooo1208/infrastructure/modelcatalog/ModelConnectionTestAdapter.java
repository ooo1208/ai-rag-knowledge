package io.github.ooo1208.infrastructure.modelcatalog;

import io.github.ooo1208.application.chat.model.ConnectionTestStatus;
import io.github.ooo1208.application.chat.model.ModelConnectionTestResult;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.port.out.ModelConnectionTestPort;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import io.github.ooo1208.infrastructure.config.OpenAiApiSupport;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;

/**
 * 供应商连接轻量探针。
 *
 * <p>Ollama 使用模型列表接口，OpenAI Compatible 使用 {@code /v1/models}。
 * 探针不会调用模型生成内容，也不会把上游响应正文或凭证写入结果。</p>
 */
@Component
public final class ModelConnectionTestAdapter
        implements ModelConnectionTestPort {

    private final CredentialResolver credentialResolver;

    public ModelConnectionTestAdapter(CredentialResolver credentialResolver) {
        this.credentialResolver = Objects.requireNonNull(credentialResolver);
    }

    @Override
    public ModelConnectionTestResult test(ResolvedModelConfig modelConfig) {
        Objects.requireNonNull(modelConfig, "modelConfig");

        return switch (modelConfig.providerType()) {
            case OLLAMA -> testOllama(modelConfig);
            case OPENAI_COMPATIBLE -> testOpenAiCompatible(modelConfig);
        };
    }

    private ModelConnectionTestResult testOllama(
            ResolvedModelConfig modelConfig
    ) {
        try {
            OllamaApi.ListModelResponse response = OllamaApi.builder()
                    .baseUrl(modelConfig.baseUrl())
                    .build()
                    .listModels();

            List<OllamaApi.Model> models = response == null
                    || response.models() == null
                    ? List.of()
                    : response.models();

            boolean modelFound = models.stream()
                    .anyMatch(model -> model != null
                            && (modelConfig.upstreamModelId()
                            .equals(model.name())
                            || modelConfig.upstreamModelId()
                            .equals(model.model())));

            return modelFound
                    ? success(modelConfig, "Ollama connection and model are available.")
                    : failure(
                            modelConfig,
                            ConnectionTestStatus.MODEL_NOT_FOUND,
                            "Ollama is reachable, but the configured model was not found."
                    );
        } catch (RestClientResponseException exception) {
            return failure(
                    modelConfig,
                    classifyHttpFailure(exception),
                    "Ollama rejected the connection probe."
            );
        } catch (ResourceAccessException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.NETWORK_ERROR,
                    "Ollama could not be reached."
            );
        } catch (RestClientException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.PROVIDER_UNAVAILABLE,
                    "Ollama returned an unusable response."
            );
        } catch (IllegalArgumentException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.UNSUPPORTED,
                    "Ollama connection configuration is invalid."
            );
        }
    }

    private ModelConnectionTestResult testOpenAiCompatible(
            ResolvedModelConfig modelConfig
    ) {
        String apiKey;
        try {
            apiKey = credentialResolver.resolve(modelConfig.credentialRef());
        } catch (IllegalArgumentException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.AUTHENTICATION_FAILED,
                    "OpenAI-compatible credentials are not configured."
            );
        }

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(
                            OpenAiApiSupport.normalizeBaseUrl(
                                    modelConfig.baseUrl()
                            )
                    )
                    .defaultHeaders(headers -> headers.setBearerAuth(apiKey))
                    .build();

            OpenAiModelListResponse response = restClient.get()
                    .uri("/v1/models")
                    .retrieve()
                    .body(OpenAiModelListResponse.class);

            List<OpenAiModelDescriptor> models = response == null
                    || response.data() == null
                    ? List.of()
                    : response.data();

            boolean modelFound = models.stream()
                    .anyMatch(model -> model != null
                            && modelConfig.upstreamModelId()
                            .equals(model.id()));

            return modelFound
                    ? success(
                            modelConfig,
                            "OpenAI-compatible connection and model are available."
                    )
                    : failure(
                            modelConfig,
                            ConnectionTestStatus.MODEL_NOT_FOUND,
                            "OpenAI-compatible provider is reachable, but the configured model was not found."
                    );
        } catch (RestClientResponseException exception) {
            return failure(
                    modelConfig,
                    classifyHttpFailure(exception),
                    "OpenAI-compatible provider rejected the connection probe."
            );
        } catch (ResourceAccessException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.NETWORK_ERROR,
                    "OpenAI-compatible provider could not be reached."
            );
        } catch (IllegalArgumentException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.UNSUPPORTED,
                    "OpenAI-compatible connection configuration is invalid."
            );
        } catch (RestClientException exception) {
            return failure(
                    modelConfig,
                    ConnectionTestStatus.PROVIDER_UNAVAILABLE,
                    "OpenAI-compatible provider returned an unusable response."
            );
        }
    }

    private ConnectionTestStatus classifyHttpFailure(
            RestClientResponseException exception
    ) {
        int statusCode = exception.getStatusCode().value();
        if (statusCode == 401 || statusCode == 403) {
            return ConnectionTestStatus.AUTHENTICATION_FAILED;
        }
        if (statusCode >= 500) {
            return ConnectionTestStatus.PROVIDER_UNAVAILABLE;
        }
        return ConnectionTestStatus.UNSUPPORTED;
    }

    private ModelConnectionTestResult success(
            ResolvedModelConfig modelConfig,
            String message
    ) {
        return new ModelConnectionTestResult(
                modelConfig.modelConfigId(),
                ConnectionTestStatus.SUCCESS,
                message
        );
    }

    private ModelConnectionTestResult failure(
            ResolvedModelConfig modelConfig,
            ConnectionTestStatus status,
            String message
    ) {
        return new ModelConnectionTestResult(
                modelConfig.modelConfigId(),
                status,
                message
        );
    }

    private record OpenAiModelListResponse(
            List<OpenAiModelDescriptor> data
    ) {
    }

    private record OpenAiModelDescriptor(String id) {
    }
}
