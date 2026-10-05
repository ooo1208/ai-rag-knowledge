package io.github.ooo1208.application.chat.port.out;

import io.github.ooo1208.application.chat.model.RetrievedDocument;

import java.util.List;

// 根据用户问题和知识库标签，检索相关文档。
/**
 * 出站端口：按用户问题和知识库标签检索相关文档。
 * 不负责 Prompt 组装，也不负责调用聊天模型。
 */
public interface RagRetrieverPort {
    List<RetrievedDocument> retrieve(String query, int topK, String ragTag);
}
