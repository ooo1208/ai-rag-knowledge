package io.github.ooo1208.application.modelcatalog;

import io.github.ooo1208.application.modelcatalog.model.ModelConfigSummary;
import io.github.ooo1208.application.modelcatalog.service.ModelConfigCatalogApplicationService;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;
import io.github.ooo1208.domain.modelcatalog.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 模型选择器目录用例的纯 application 单元测试。
 */
class ModelConfigCatalogApplicationServiceTest {

    @Test
    void listReturnsSafeSummariesFromQueryPort() {
        ModelConfigSummary summary = new ModelConfigSummary(
                new ModelConfigId("ollama-local"),
                "Ollama Local",
                ProviderType.OLLAMA,
                "TEXT,RAG",
                true
        );
        ModelConfigCatalogApplicationService service =
                new ModelConfigCatalogApplicationService(
                        () -> List.of(summary)
                );

        assertEquals(List.of(summary), service.list());
    }
}
