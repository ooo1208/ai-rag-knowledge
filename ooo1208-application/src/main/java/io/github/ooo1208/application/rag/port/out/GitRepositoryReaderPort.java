package io.github.ooo1208.application.rag.port.out;

import io.github.ooo1208.application.rag.command.AnalyzeGitRepositoryCommand;
import io.github.ooo1208.application.rag.model.RagRepositorySnapshot;

/**
 * 出站端口：从代码仓库读取可索引文件。
 * 具体 Git 客户端由 infrastructure 层决定。
 */
public interface GitRepositoryReaderPort {

    RagRepositorySnapshot read(AnalyzeGitRepositoryCommand command);
}
