package io.github.ooo1208.application.rag.port.out;

import java.util.List;

/**
 * 出站端口：保存和查询知识库标签。
 * 当前实现使用 Redis，未来可以替换为数据库。
 */
public interface RagTagStorePort {

    List<String> list();

    void add(String ragTag);
}
