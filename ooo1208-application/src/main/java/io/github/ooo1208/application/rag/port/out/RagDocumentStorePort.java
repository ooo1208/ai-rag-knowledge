package io.github.ooo1208.application.rag.port.out;

import io.github.ooo1208.application.rag.model.RagFile;

import java.util.List;

/**
 * 出站端口：把文件解析、切分并写入知识库文档存储。
 * application 层不关心向量库的具体实现。
 */
public interface RagDocumentStorePort {

    void store(String ragTag, List<RagFile> files);
}
