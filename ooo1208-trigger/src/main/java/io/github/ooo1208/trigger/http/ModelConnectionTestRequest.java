package io.github.ooo1208.trigger.http;

/**
 * 模型连接测试 HTTP 请求。
 *
 * <p>只允许引用已经登记的模型配置，不接受任意 base URL 或密钥。</p>
 */
public record ModelConnectionTestRequest(String modelConfigId) {

    public ModelConnectionTestRequest {
        if (modelConfigId == null || modelConfigId.isBlank()) {
            throw new IllegalArgumentException(
                    "modelConfigId must not be blank"
            );
        }
        modelConfigId = modelConfigId.trim();
    }
}
