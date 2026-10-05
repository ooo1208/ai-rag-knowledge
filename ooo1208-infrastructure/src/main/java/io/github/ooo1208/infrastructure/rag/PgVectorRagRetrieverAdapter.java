package io.github.ooo1208.infrastructure.rag;

import io.github.ooo1208.application.chat.model.RetrievedDocument;
import io.github.ooo1208.application.chat.port.out.RagRetrieverPort;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * PgVector 检索适配器。
 * 将 application 的检索请求转换成 Spring AI SearchRequest，并映射回 RetrievedDocument。
 */
@Component
public final class PgVectorRagRetrieverAdapter
        implements RagRetrieverPort {

    private static final String KNOWLEDGE_METADATA_KEY = "knowledge";

    private final PgVectorStore pgVectorStore;

    public PgVectorRagRetrieverAdapter(PgVectorStore pgVectorStore) {
        this.pgVectorStore = Objects.requireNonNull(pgVectorStore);
    }

    @Override
    public List<RetrievedDocument> retrieve(
            String query,
            int topK,
            String ragTag
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (ragTag == null || ragTag.isBlank()) {
            throw new IllegalArgumentException("ragTag must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }

        FilterExpressionBuilder filterBuilder =
                new FilterExpressionBuilder();

        SearchRequest request = SearchRequest.builder()
                .query(query.trim())
                .topK(topK)
                .filterExpression(
                        filterBuilder
                                .eq(KNOWLEDGE_METADATA_KEY, ragTag.trim())
                                .build()
                )
                .build();

        return pgVectorStore.similaritySearch(request)
                .stream()
                .filter(this::hasText)
                .map(this::toRetrievedDocument)
                .toList();
    }

    private boolean hasText(Document document) {
        return document.getText() != null
                && !document.getText().isBlank();
    }

    private RetrievedDocument toRetrievedDocument(Document document) {
        Double score = document.getScore();
        double similarityScore = score == null ? 0.0 : score;

        Map<String, String> metadata = document.getMetadata()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> String.valueOf(entry.getValue()),
                        (first, second) -> first
                ));

        return new RetrievedDocument(
                document.getText(),
                similarityScore,
                metadata
        );
    }
}
