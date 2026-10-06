package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.websearch.exception.NetworkSearchDisabledException;
import io.github.ooo1208.application.websearch.exception.NetworkSearchProviderException;
import io.github.ooo1208.application.websearch.model.NetworkSearchQuery;
import io.github.ooo1208.application.websearch.model.NetworkSearchResult;
import io.github.ooo1208.application.websearch.port.in.SearchWebUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * 受控联网搜索入口。
 *
 * <p>调用方只能提供搜索文本和数量上限；Provider 地址、凭证和出站策略均由
 * 服务端配置决定。返回结果明确标记为不可信外部内容。</p>
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/web-search")
public final class WebSearchController {

    private final SearchWebUseCase searchWebUseCase;

    public WebSearchController(SearchWebUseCase searchWebUseCase) {
        this.searchWebUseCase = Objects.requireNonNull(searchWebUseCase);
    }

    @GetMapping
    public List<WebSearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults
    ) {
        try {
            return searchWebUseCase.search(
                            new NetworkSearchQuery(query, maxResults)
                    )
                    .stream()
                    .map(WebSearchResponse::from)
                    .toList();
        } catch (NetworkSearchDisabledException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "web search provider is disabled"
            );
        } catch (NetworkSearchProviderException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "web search provider is unavailable"
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }

    /**
     * 不把 application 记录直接暴露成 HTTP 契约，后续可独立增加来源和时间字段。
     */
    public record WebSearchResponse(
            String title,
            String url,
            String snippet,
            boolean untrusted
    ) {

        private static WebSearchResponse from(NetworkSearchResult result) {
            return new WebSearchResponse(
                    result.title(),
                    result.url(),
                    result.snippet(),
                    result.untrusted()
            );
        }
    }
}
