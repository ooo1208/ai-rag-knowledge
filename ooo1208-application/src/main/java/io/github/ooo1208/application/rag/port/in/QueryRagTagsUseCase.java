package io.github.ooo1208.application.rag.port.in;

import java.util.List;

/**
 * 入站端口：查询系统中已有的知识库标签。
 */
public interface QueryRagTagsUseCase {

    List<String> query();
}
