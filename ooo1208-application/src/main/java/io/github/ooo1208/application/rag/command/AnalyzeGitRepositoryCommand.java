package io.github.ooo1208.application.rag.command;

/**
 * 分析 Git 仓库用例的输入命令。
 * application 层只接收字符串和业务参数，不依赖 JGit 类型。
 */
public record AnalyzeGitRepositoryCommand(
        String repoUrl,
        String username,
        String token
) {

    public AnalyzeGitRepositoryCommand {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }
        if (username == null) {
            username = "";
        }
        if (token == null) {
            token = "";
        }
        repoUrl = repoUrl.trim();
        username = username.trim();
        token = token.trim();
    }
}
