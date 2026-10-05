package io.github.ooo1208.application.rag.service;

import io.github.ooo1208.application.rag.command.AnalyzeGitRepositoryCommand;
import io.github.ooo1208.application.rag.command.UploadRagFilesCommand;
import io.github.ooo1208.application.rag.model.RagRepositorySnapshot;
import io.github.ooo1208.application.rag.port.in.AnalyzeGitRepositoryUseCase;
import io.github.ooo1208.application.rag.port.in.QueryRagTagsUseCase;
import io.github.ooo1208.application.rag.port.in.UploadRagFilesUseCase;
import io.github.ooo1208.application.rag.port.out.GitRepositoryReaderPort;
import io.github.ooo1208.application.rag.port.out.RagDocumentStorePort;
import io.github.ooo1208.application.rag.port.out.RagTagStorePort;

import java.util.List;
import java.util.Objects;

/**
 * 知识库用例编排器。
 * 只编排文件存储、标签登记和 Git 读取，不直接操作 Tika、PgVector、Redis 或 JGit。
 */
public final class RagKnowledgeApplicationService
        implements QueryRagTagsUseCase, UploadRagFilesUseCase, AnalyzeGitRepositoryUseCase {

    private final RagDocumentStorePort documentStore;
    private final RagTagStorePort tagStore;
    private final GitRepositoryReaderPort repositoryReader;

    public RagKnowledgeApplicationService(
            RagDocumentStorePort documentStore,
            RagTagStorePort tagStore,
            GitRepositoryReaderPort repositoryReader
    ) {
        this.documentStore = Objects.requireNonNull(documentStore);
        this.tagStore = Objects.requireNonNull(tagStore);
        this.repositoryReader = Objects.requireNonNull(repositoryReader);
    }

    @Override
    public List<String> query() {
        return List.copyOf(tagStore.list());
    }

    @Override
    public void upload(UploadRagFilesCommand command) {
        Objects.requireNonNull(command, "command");
        documentStore.store(command.ragTag(), command.files());
        tagStore.add(command.ragTag());
    }

    @Override
    public void analyze(AnalyzeGitRepositoryCommand command) {
        Objects.requireNonNull(command, "command");
        RagRepositorySnapshot snapshot = repositoryReader.read(command);
        documentStore.store(snapshot.ragTag(), snapshot.files());
        tagStore.add(snapshot.ragTag());
    }
}
