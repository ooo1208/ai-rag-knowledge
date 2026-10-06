package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.chat.command.TestModelConnectionCommand;
import io.github.ooo1208.application.chat.model.ModelConnectionTestResult;
import io.github.ooo1208.application.chat.port.in.TestModelConnectionUseCase;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 模型连接测试 HTTP 入口。
 *
 * <p>管理员 API 和权限控制尚未接入前，这里只暴露对已登记且启用的
 * {@code modelConfigId} 的轻量探针，不接受任意外部地址。</p>
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/model-connections")
public class ModelConnectionController {

    private final TestModelConnectionUseCase testModelConnectionUseCase;

    public ModelConnectionController(
            TestModelConnectionUseCase testModelConnectionUseCase
    ) {
        this.testModelConnectionUseCase = Objects.requireNonNull(
                testModelConnectionUseCase
        );
    }

    @PostMapping(
            value = "/test",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ModelConnectionTestResponse test(
            @RequestBody ModelConnectionTestRequest request
    ) {
        ModelConnectionTestResult result = testModelConnectionUseCase.test(
                new TestModelConnectionCommand(
                        new ModelConfigId(request.modelConfigId())
                )
        );
        return ModelConnectionTestResponse.from(result);
    }
}
