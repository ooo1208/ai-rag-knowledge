package io.github.ooo1208.application.chat.model;

import java.util.Map;

/**
 * application 层使用的检索结果。
 * 它不暴露 PgVector 或 Spring AI Document，PromptAssembler 只依赖这个业务模型。
 *
 * @param content 文档内容
 * @param similarityScore 文档与查询的相似度分数
 * @param metadata 文档元数据
 */
public record RetrievedDocument(
        String content,
        double similarityScore,
        Map<String, String> metadata
) {

    public RetrievedDocument {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "content must not be blank"
            );
        }

        if (Double.isNaN(similarityScore)
                || Double.isInfinite(similarityScore)
                || similarityScore < 0) {
            throw new IllegalArgumentException(
                    "similarityScore must be a valid non-negative number"
            );
        }

        content = content.trim();

        metadata = metadata == null
                ? Map.of()
                : Map.copyOf(metadata);
    }
}
