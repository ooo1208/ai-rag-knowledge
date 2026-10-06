package io.github.ooo1208.application.chat.model;

import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.Objects;

/**
 * 模型连接测试结果，不包含 API Key、Base URL 或上游响应正文。
 */
public record ModelConnectionTestResult(
        ModelConfigId modelConfigId,
        ConnectionTestStatus status,
        String message
) {

    public ModelConnectionTestResult {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        Objects.requireNonNull(status, "status");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
    }

    public boolean successful() {
        return status == ConnectionTestStatus.SUCCESS;
    }
}
