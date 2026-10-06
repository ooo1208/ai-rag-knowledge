package io.github.ooo1208.application.websearch.port.in;

import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;

import java.util.List;

/**
 * 联网搜索入站用例。
 */
public interface SearchWebUseCase {

    List<NetworkSearchResult> search(NetworkSearchQuery query);
}
