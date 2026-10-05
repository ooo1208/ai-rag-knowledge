package io.github.ooo1208.application.rag.port.in;

import io.github.ooo1208.application.rag.command.AnalyzeGitRepositoryCommand;

/**
 * 入站端口：读取并分析一个 Git 仓库，建立对应知识库。
 */
public interface AnalyzeGitRepositoryUseCase {

    void analyze(AnalyzeGitRepositoryCommand command);
}
