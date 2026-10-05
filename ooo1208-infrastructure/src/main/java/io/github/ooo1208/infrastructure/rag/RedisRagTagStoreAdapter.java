package io.github.ooo1208.infrastructure.rag;

import io.github.ooo1208.application.rag.port.out.RagTagStorePort;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Redis 知识库标签适配器。
 * 当前只保存标签列表，不承载聊天业务和模型配置数据。
 */
@Component
public final class RedisRagTagStoreAdapter implements RagTagStorePort {

    private static final String RAG_TAG_KEY = "ragTag";

    private final RedissonClient redissonClient;

    public RedisRagTagStoreAdapter(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient);
    }

    @Override
    public List<String> list() {
        RList<String> tags = redissonClient.getList(RAG_TAG_KEY);
        return List.copyOf(tags);
    }

    @Override
    public void add(String ragTag) {
        if (ragTag == null || ragTag.isBlank()) {
            throw new IllegalArgumentException("ragTag must not be blank");
        }

        RList<String> tags = redissonClient.getList(RAG_TAG_KEY);
        String normalizedTag = ragTag.trim();
        if (!tags.contains(normalizedTag)) {
            tags.add(normalizedTag);
        }
    }
}
