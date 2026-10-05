package io.github.ooo1208.application.rag.model;

import java.util.List;

/**
 * Git 仓库读取端口返回的快照。
 * ragTag 使用仓库名，files 是待写入向量库的文件集合。
 */
public record RagRepositorySnapshot(String ragTag, List<RagFile> files) {

    public RagRepositorySnapshot {
        if (ragTag == null || ragTag.isBlank()) {
            throw new IllegalArgumentException("ragTag must not be blank");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        ragTag = ragTag.trim();
        files = List.copyOf(files);
    }
}
