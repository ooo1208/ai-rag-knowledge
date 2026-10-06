package io.github.ooo1208.application.websearch.service;

import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import io.github.ooo1208.application.websearch.port.in.SearchWebUseCase;
import io.github.ooo1208.application.websearch.port.out.NetworkSearchPort;

import java.util.List;
import java.util.Objects;

/**
 * 联网搜索用例编排器。
 *
 * <p>Provider 是否启用、HTTP 请求、凭证和出站网络策略都属于基础设施层；
 * application 层只校验查询并保证返回集合不可变。</p>
 */
public final class NetworkSearchApplicationService
        implements SearchWebUseCase {

    private final NetworkSearchPort networkSearchPort;

    public NetworkSearchApplicationService(NetworkSearchPort networkSearchPort) {
        this.networkSearchPort = Objects.requireNonNull(networkSearchPort);
    }

    @Override
    public List<NetworkSearchResult> search(NetworkSearchQuery query) {
        Objects.requireNonNull(query, "query");
        List<NetworkSearchResult> results = networkSearchPort.search(query);
        return results == null ? List.of() : List.copyOf(results);
    }
}
