package io.github.ooo1208.infrastructure.rag;

import io.github.ooo1208.application.rag.model.RagFile;
import io.github.ooo1208.application.rag.port.out.RagDocumentStorePort;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * PgVector 文档存储适配器。
 * 负责 Tika 解析、Token 切分、知识库元数据写入和向量持久化。
 */
@Component
public final class PgVectorRagDocumentStoreAdapter
        implements RagDocumentStorePort {

    private static final String KNOWLEDGE_METADATA_KEY = "knowledge";
    private static final Logger log = LoggerFactory.getLogger(
            PgVectorRagDocumentStoreAdapter.class
    );

    private final PgVectorStore pgVectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    public PgVectorRagDocumentStoreAdapter(
            PgVectorStore pgVectorStore,
            TokenTextSplitter tokenTextSplitter
    ) {
        this.pgVectorStore = Objects.requireNonNull(pgVectorStore);
        this.tokenTextSplitter = Objects.requireNonNull(tokenTextSplitter);
    }

    @Override
    public void store(String ragTag, List<RagFile> files) {
        if (ragTag == null || ragTag.isBlank()) {
            throw new IllegalArgumentException("ragTag must not be blank");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }

        int storedChunkCount = 0;
        for (RagFile file : files) {
            try {
                List<Document> documents = new TikaDocumentReader(
                        new NamedByteArrayResource(
                                file.content(),
                                file.filename()
                        )
                ).get();

                List<Document> chunks = tokenTextSplitter.apply(documents);
                chunks.forEach(document -> document.getMetadata()
                        .put(KNOWLEDGE_METADATA_KEY, ragTag.trim()));

                if (!chunks.isEmpty()) {
                    pgVectorStore.accept(chunks);
                    storedChunkCount += chunks.size();
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "Skip unsupported knowledge file: {}",
                        file.filename(),
                        exception
                );
            }
        }

        if (storedChunkCount == 0) {
            throw new IllegalStateException(
                    "No readable document content found for ragTag: " + ragTag
            );
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
