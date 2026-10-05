package io.github.ooo1208.config;

import io.github.ooo1208.application.rag.port.out.GitRepositoryReaderPort;
import io.github.ooo1208.application.rag.port.out.RagDocumentStorePort;
import io.github.ooo1208.application.rag.port.out.RagTagStorePort;
import io.github.ooo1208.application.rag.service.RagKnowledgeApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库应用层装配配置。
 * 把文档存储、标签存储和 Git 读取适配器接入 RagKnowledgeApplicationService。
 */
@Configuration
public class RagApplicationConfiguration {

    @Bean
    public RagKnowledgeApplicationService ragKnowledgeApplicationService(
            RagDocumentStorePort documentStore,
            RagTagStorePort tagStore,
            GitRepositoryReaderPort repositoryReader
    ) {
        return new RagKnowledgeApplicationService(
                documentStore,
                tagStore,
                repositoryReader
        );
    }
}
