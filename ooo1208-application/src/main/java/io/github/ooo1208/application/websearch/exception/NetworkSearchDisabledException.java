package io.github.ooo1208.application.websearch.exception;

/**
 * 搜索 Provider 未显式启用时返回的稳定异常。
 */
public final class NetworkSearchDisabledException extends RuntimeException {

    public NetworkSearchDisabledException() {
        super("web search provider is disabled");
    }
}
