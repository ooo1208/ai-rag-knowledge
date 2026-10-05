package io.github.ooo1208.application.rag.model;

import java.util.Arrays;

/**
 * 已准备好进入知识库解析和索引流程的文件。
 * application 层用它替代 MultipartFile，避免业务层依赖 HTTP 框架。
 */
public record RagFile(String filename, byte[] content) {

    public RagFile {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        filename = filename.trim();
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
