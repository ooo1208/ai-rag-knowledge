package io.github.ooo1208.infrastructure.rag;

import io.github.ooo1208.application.rag.command.AnalyzeGitRepositoryCommand;
import io.github.ooo1208.application.rag.model.RagFile;
import io.github.ooo1208.application.rag.model.RagRepositorySnapshot;
import io.github.ooo1208.application.rag.port.out.GitRepositoryReaderPort;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * JGit 仓库读取适配器。
 * 负责临时克隆仓库、读取文件并转换成 application 层的 RagRepositorySnapshot。
 */
@Component
public final class JGitRepositoryReaderAdapter
        implements GitRepositoryReaderPort {

    @Override
    public RagRepositorySnapshot read(AnalyzeGitRepositoryCommand command) {
        Path checkoutDirectory = null;
        try {
            checkoutDirectory = Files.createTempDirectory("ai-rag-git-");
            cloneRepository(command, checkoutDirectory);

            List<RagFile> files = new ArrayList<>();
            Path finalCheckoutDirectory = checkoutDirectory;
            Files.walkFileTree(finalCheckoutDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(
                        Path directory,
                        BasicFileAttributes attributes
                ) {
                    if (directory.getFileName() != null
                            && ".git".equals(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attributes
                ) {
                    try {
                        byte[] content = Files.readAllBytes(file);
                        if (content.length > 0) {
                            files.add(new RagFile(
                                    finalCheckoutDirectory
                                            .relativize(file)
                                            .toString(),
                                    content
                            ));
                        }
                    } catch (IOException ignored) {
                        // A repository may contain binary or unreadable files.
                        // The document adapter will decide what it can parse.
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (files.isEmpty()) {
                throw new IllegalStateException(
                        "No readable files found in repository: "
                                + command.repoUrl()
                );
            }

            return new RagRepositorySnapshot(
                    extractRepositoryName(command.repoUrl()),
                    files
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to read Git repository: " + command.repoUrl(),
                    exception
            );
        } finally {
            if (checkoutDirectory != null) {
                deleteRecursively(checkoutDirectory);
            }
        }
    }

    private void cloneRepository(
            AnalyzeGitRepositoryCommand command,
            Path checkoutDirectory
    ) throws Exception {
        var cloneCommand = Git.cloneRepository()
                .setURI(command.repoUrl())
                .setDirectory(checkoutDirectory.toFile());

        if (!command.username().isBlank() || !command.token().isBlank()) {
            cloneCommand.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(
                            command.username(),
                            command.token()
                    )
            );
        }

        try (Git ignored = cloneCommand.call()) {
            // The try-with-resources block closes the Git transport.
        }
    }

    private String extractRepositoryName(String repoUrl) {
        String normalizedUrl = repoUrl.endsWith("/")
                ? repoUrl.substring(0, repoUrl.length() - 1)
                : repoUrl;
        String repositoryName = normalizedUrl.substring(
                normalizedUrl.lastIndexOf('/') + 1
        );
        return repositoryName.endsWith(".git")
                ? repositoryName.substring(0, repositoryName.length() - 4)
                : repositoryName;
    }

    private void deleteRecursively(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attributes
                ) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir,
                        IOException exception
                ) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Cleanup must not hide the repository read result.
        }
    }
}
