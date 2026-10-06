package io.github.ooo1208.application.chat.port.in;

import io.github.ooo1208.application.chat.command.TestModelConnectionCommand;
import io.github.ooo1208.application.chat.model.ModelConnectionTestResult;

/**
 * 入站端口：测试一个已经登记的模型配置是否可用。
 */
public interface TestModelConnectionUseCase {

    ModelConnectionTestResult test(TestModelConnectionCommand command);
}
