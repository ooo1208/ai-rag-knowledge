package io.github.ooo1208.application.chat.command;

import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.util.Objects;

/**
 * 测试一个已登记模型配置的连接命令。
 *
 * <p>命令只携带稳定的 {@code modelConfigId}，不允许调用方直接提交任意 URL
 * 或 API Key。</p>
 */
public record TestModelConnectionCommand(ModelConfigId modelConfigId) {

    public TestModelConnectionCommand {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
    }
}
