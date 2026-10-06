package io.github.ooo1208.application.chat.service;

import io.github.ooo1208.application.chat.command.TestModelConnectionCommand;
import io.github.ooo1208.application.chat.model.ModelConnectionTestResult;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;
import io.github.ooo1208.application.chat.port.in.TestModelConnectionUseCase;
import io.github.ooo1208.application.chat.port.out.ModelConfigQueryPort;
import io.github.ooo1208.application.chat.port.out.ModelConnectionTestPort;

import java.util.Objects;

/**
 * 模型连接测试用例编排器。
 *
 * <p>先使用和聊天相同的模型目录解析链路，再把完整配置交给基础设施探针，
 * 保证测试结果与实际聊天使用的配置一致。</p>
 */
public final class ModelConnectionTestApplicationService
        implements TestModelConnectionUseCase {

    private final ModelConfigQueryPort modelConfigQueryPort;
    private final ModelConnectionTestPort modelConnectionTestPort;

    public ModelConnectionTestApplicationService(
            ModelConfigQueryPort modelConfigQueryPort,
            ModelConnectionTestPort modelConnectionTestPort
    ) {
        this.modelConfigQueryPort = Objects.requireNonNull(
                modelConfigQueryPort
        );
        this.modelConnectionTestPort = Objects.requireNonNull(
                modelConnectionTestPort
        );
    }

    @Override
    public ModelConnectionTestResult test(
            TestModelConnectionCommand command
    ) {
        Objects.requireNonNull(command, "command");

        ResolvedModelConfig modelConfig =
                modelConfigQueryPort.queryModelConfig(command.modelConfigId());
        return modelConnectionTestPort.test(modelConfig);
    }
}
