package io.github.ooo1208.application.rag.port.in;

import io.github.ooo1208.application.rag.command.UploadRagFilesCommand;

/**
 * 入站端口：上传文件并写入知识库。
 */
public interface UploadRagFilesUseCase {

    void upload(UploadRagFilesCommand command);
}
