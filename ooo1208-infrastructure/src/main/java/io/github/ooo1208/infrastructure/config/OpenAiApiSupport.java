package io.github.ooo1208.infrastructure.config;

import org.springframework.ai.openai.api.OpenAiApi;

import java.util.Objects;

/**
 * 统一构造 OpenAI Compatible API，兼容带或不带 {@code /v1} 的 base URL。
 *
 * <p>Spring AI 1.0.2 的默认 completions 和 embeddings 路径已经包含
 * {@code /v1}，因此这里把 base URL 末尾的版本路径去掉，避免出现重复路径。</p>
 */
public final class OpenAiApiSupport {

    private OpenAiApiSupport() {
    }

    public static OpenAiApi create(String baseUrl, String apiKey) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");

        return OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .apiKey(apiKey)
                .build();
    }

    static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return normalized;
    }
}
