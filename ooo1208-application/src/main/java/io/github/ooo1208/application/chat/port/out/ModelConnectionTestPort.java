package io.github.ooo1208.application.chat.port.out;

import io.github.ooo1208.application.chat.model.ModelConnectionTestResult;
import io.github.ooo1208.application.chat.model.ResolvedModelConfig;

/**
 * 出站端口：调用具体服务商的轻量探针测试连接和模型可用性。
 */
public interface ModelConnectionTestPort {

    ModelConnectionTestResult test(ResolvedModelConfig modelConfig);
}
