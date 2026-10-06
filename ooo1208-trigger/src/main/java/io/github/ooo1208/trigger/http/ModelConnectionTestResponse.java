package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.chat.model.ConnectionTestStatus;
import io.github.ooo1208.application.chat.model.ModelConnectionTestResult;

import java.util.Objects;

/**
 * 模型连接测试 HTTP 响应，避免把 domain 的 ModelConfigId 结构直接暴露给前端。
 */
public record ModelConnectionTestResponse(
        String modelConfigId,
        ConnectionTestStatus status,
        String message,
        boolean successful
) {

    public static ModelConnectionTestResponse from(
            ModelConnectionTestResult result
    ) {
        Objects.requireNonNull(result, "result");
        return new ModelConnectionTestResponse(
                result.modelConfigId().value(),
                result.status(),
                result.message(),
                result.successful()
        );
    }
}
