package io.github.ooo1208.application.websearch.port.out;

import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;

import java.util.List;

/**
 * 固定联网 Provider 的出站端口。
 *
 * <p>application 层不依赖 Tavily、Spring Web 或具体 HTTP 协议。实现必须
 * 只访问服务端配置的 Provider，并返回已经标记为不可信的摘要。</p>
 */
public interface NetworkSearchPort {

    List<NetworkSearchResult> search(NetworkSearchQuery query);
}
