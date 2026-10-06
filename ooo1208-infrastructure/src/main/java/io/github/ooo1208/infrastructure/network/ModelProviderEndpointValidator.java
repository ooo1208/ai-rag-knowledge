package io.github.ooo1208.infrastructure.network;

import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校验数据库中动态模型 Provider 的出站地址。
 *
 * <p>模型连接地址来自目录数据，不能因为是管理员写入就直接交给 HTTP
 * 客户端。不同 Provider 使用独立的 host、端口和私网策略，避免把公网
 * OpenAI-compatible 连接和内网 Ollama 连接混成一条宽松规则。</p>
 */
@Component
public final class ModelProviderEndpointValidator {

    private final EndpointPolicy ollamaPolicy;
    private final EndpointPolicy openAiCompatiblePolicy;

    @Autowired
    public ModelProviderEndpointValidator(
            @Value("${app.model-provider.outbound.ollama.allowed-hosts:192.168.23.100}")
            String ollamaAllowedHosts,
            @Value("${app.model-provider.outbound.ollama.allowed-ports:11434}")
            String ollamaAllowedPorts,
            @Value("${app.model-provider.outbound.ollama.allow-private:true}")
            boolean ollamaAllowPrivate,
            @Value("${app.model-provider.outbound.openai-compatible.allowed-hosts:api.openai.com}")
            String openAiAllowedHosts,
            @Value("${app.model-provider.outbound.openai-compatible.allowed-ports:443}")
            String openAiAllowedPorts,
            @Value("${app.model-provider.outbound.openai-compatible.allow-private:false}")
            boolean openAiAllowPrivate
    ) {
        this(
                new EndpointPolicy(
                        OutboundHostAllowlist.fromCsv(ollamaAllowedHosts),
                        parsePorts(ollamaAllowedPorts, "Ollama"),
                        ollamaAllowPrivate
                ),
                new EndpointPolicy(
                        OutboundHostAllowlist.fromCsv(openAiAllowedHosts),
                        parsePorts(openAiAllowedPorts, "OpenAI-compatible"),
                        openAiAllowPrivate
                )
        );
    }

    public ModelProviderEndpointValidator(
            EndpointPolicy ollamaPolicy,
            EndpointPolicy openAiCompatiblePolicy
    ) {
        this.ollamaPolicy = requirePolicy(ollamaPolicy, "ollamaPolicy");
        this.openAiCompatiblePolicy = requirePolicy(
                openAiCompatiblePolicy,
                "openAiCompatiblePolicy"
        );
    }

    public OutboundUrlValidator.ValidatedUrl validate(
            ProviderType providerType,
            String rawUrl
    ) {
        if (providerType == null) {
            throw new IllegalArgumentException("providerType must not be null");
        }
        EndpointPolicy policy = switch (providerType) {
            case OLLAMA -> ollamaPolicy;
            case OPENAI_COMPATIBLE -> openAiCompatiblePolicy;
        };
        return OutboundUrlValidator.validateManagedProvider(
                rawUrl,
                policy.allowedHosts(),
                policy.allowedPorts(),
                policy.allowPrivateAddresses()
        );
    }

    /**
     * 与仓库默认 application.yml 相同的策略，供保持旧构造器兼容的本地
     * 调用方和单元测试使用；Spring 运行时仍使用配置注入的策略。
     */
    public static ModelProviderEndpointValidator defaults() {
        return new ModelProviderEndpointValidator(
                new EndpointPolicy(
                        OutboundHostAllowlist.of("192.168.23.100"),
                        Set.of(11_434),
                        true
                ),
                new EndpointPolicy(
                        OutboundHostAllowlist.of("api.openai.com"),
                        Set.of(443),
                        false
                )
        );
    }

    private static EndpointPolicy requirePolicy(
            EndpointPolicy policy,
            String name
    ) {
        if (policy == null) {
            throw new NullPointerException(name);
        }
        if (policy.allowedHosts().isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must contain at least one allowed host"
            );
        }
        if (policy.allowedPorts().isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must contain at least one allowed port"
            );
        }
        return policy;
    }

    private static Set<Integer> parsePorts(String rawPorts, String provider) {
        if (rawPorts == null || rawPorts.isBlank()) {
            throw new IllegalArgumentException(
                    provider + " allowed ports must not be blank"
            );
        }
        try {
            Set<Integer> ports = Arrays.stream(rawPorts.split(","))
                    .map(String::trim)
                    .filter(port -> !port.isBlank())
                    .map(Integer::parseInt)
                    .collect(Collectors.toUnmodifiableSet());
            if (ports.isEmpty()
                    || ports.stream().anyMatch(
                    port -> port < 1 || port > 65_535
            )) {
                throw new IllegalArgumentException(
                        provider + " allowed ports must be between 1 and 65535"
                );
            }
            return ports;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    provider + " allowed ports contain an invalid value",
                    exception
            );
        }
    }

    public record EndpointPolicy(
            OutboundHostAllowlist allowedHosts,
            Set<Integer> allowedPorts,
            boolean allowPrivateAddresses
    ) {

        public EndpointPolicy {
            if (allowedHosts == null) {
                throw new NullPointerException("allowedHosts");
            }
            if (allowedPorts == null || allowedPorts.isEmpty()) {
                throw new IllegalArgumentException(
                        "allowedPorts must not be empty"
                );
            }
            allowedPorts = Set.copyOf(allowedPorts);
        }
    }
}
