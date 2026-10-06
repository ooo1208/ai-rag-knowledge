package io.github.ooo1208.application.websearch.exception;

/**
 * 搜索 Provider 请求失败时使用的稳定异常。
 *
 * <p>异常消息不能包含响应正文、请求 URL 中的凭证或 API Key。</p>
 */
public final class NetworkSearchProviderException extends RuntimeException {

    public NetworkSearchProviderException(String message) {
        super(message);
    }

    public NetworkSearchProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
