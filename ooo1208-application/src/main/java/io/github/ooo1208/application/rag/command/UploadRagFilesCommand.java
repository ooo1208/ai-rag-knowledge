package io.github.ooo1208.application.rag.command;

import io.github.ooo1208.application.rag.model.RagFile;

import java.util.List;

/**
 * 上传本地知识库文件的输入命令。
 * MultipartFile 会在 trigger 层转换成 application 自己的 RagFile。
 */
public record UploadRagFilesCommand(String ragTag, List<RagFile> files) {

    public UploadRagFilesCommand {
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
