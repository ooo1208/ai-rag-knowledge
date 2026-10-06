package io.github.ooo1208.trigger.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 在 JSON 反序列化前限制 MCP 工具请求体大小。
 *
 * <p>application 层还会限制参数结构和总字符预算，但那些检查发生在
 * MVC 已经读取请求体之后；这里先挡住 Content-Length 明确过大的请求，
 * 并对未知长度请求先做受限缓存，避免 JSON 解析提前结束后仍留下超限
 * 的尾部数据。</p>
 */
@Component
public final class McpToolRequestSizeFilter extends OncePerRequestFilter {

    private static final long MIN_REQUEST_BYTES = 4 * 1024;
    private static final long MAX_REQUEST_BYTES = 1024 * 1024;

    private final long maxRequestBytes;

    public McpToolRequestSizeFilter(
            @Value("${app.mcp.execution.max-request-bytes:131072}")
            long maxRequestBytes
    ) {
        if (maxRequestBytes < MIN_REQUEST_BYTES
                || maxRequestBytes > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException(
                    "MCP execution max-request-bytes must be between "
                            + MIN_REQUEST_BYTES + " and " + MAX_REQUEST_BYTES
            );
        }
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri != null && contextPath != null
                && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }
        try {
            requestUri = requestUri == null
                    ? null
                    : UriUtils.decode(requestUri, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            // 解码失败时按命中处理，避免畸形编码绕过请求体边界。
            return false;
        }
        return !HttpMethod.POST.matches(request.getMethod())
                || requestUri == null
                || !requestUri.startsWith("/api/v1/model-configs/")
                || !requestUri.endsWith("/execute");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestBytes) {
            sendTooLarge(response);
            return;
        }
        try {
            if (contentLength < 0) {
                byte[] body = readUnknownLengthBody(request);
                filterChain.doFilter(
                        new BufferedRequest(request, body),
                        response
                );
                return;
            }
            filterChain.doFilter(
                    new BoundedRequest(request, maxRequestBytes),
                    response
            );
        } catch (RequestTooLargeException exception) {
            if (!response.isCommitted()) {
                sendTooLarge(response);
                return;
            }
            throw exception;
        } catch (IOException exception) {
            if (!response.isCommitted()
                    && exception.getMessage() != null
                    && exception.getMessage().contains("exceeds configured limit")) {
                sendTooLarge(response);
                return;
            }
            throw exception;
        }
    }

    private byte[] readUnknownLengthBody(HttpServletRequest request)
            throws IOException {
        try (ServletInputStream input = request.getInputStream()) {
            ByteArrayOutputStream body = new ByteArrayOutputStream(
                    (int) Math.min(maxRequestBytes, 8 * 1024)
            );
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if ((long) body.size() + read > maxRequestBytes) {
                    throw new RequestTooLargeException();
                }
                body.write(buffer, 0, read);
            }
            return body.toByteArray();
        }
    }

    private void sendTooLarge(HttpServletResponse response) throws IOException {
        response.sendError(
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "MCP tool request body is too large"
        );
    }

    private static final class RequestTooLargeException extends IOException {

        private RequestTooLargeException() {
            super("MCP tool request body exceeds configured limit");
        }
    }

    private static final class BufferedRequest
            extends HttpServletRequestWrapper {

        private final byte[] body;
        private BufferedServletInputStream inputStream;

        private BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            if (inputStream == null) {
                inputStream = new BufferedServletInputStream(body);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), charset)
            );
        }
    }

    private static final class BufferedServletInputStream
            extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private BufferedServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public long skip(long amount) {
            return delegate.skip(amount);
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(
                jakarta.servlet.ReadListener readListener
        ) {
            throw new UnsupportedOperationException(
                    "async read listener is not supported by the buffered request"
            );
        }
    }

    private static final class BoundedRequest
            extends HttpServletRequestWrapper {

        private final long maxBytes;
        private BoundedServletInputStream boundedInputStream;

        private BoundedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (boundedInputStream == null) {
                boundedInputStream = new BoundedServletInputStream(
                        super.getInputStream(),
                        maxBytes
                );
            }
            return boundedInputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), charset)
            );
        }
    }

    private static final class BoundedServletInputStream
            extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        private BoundedServletInputStream(
                ServletInputStream delegate,
                long maxBytes
        ) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maxBytes) {
                int extra = delegate.read();
                if (extra >= 0) {
                    throw tooLarge();
                }
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
                throws IOException {
            if (length == 0) {
                return 0;
            }
            long remaining = maxBytes - bytesRead;
            if (remaining <= 0) {
                return read();
            }
            int allowedLength = (int) Math.min(remaining, length);
            int read = delegate.read(buffer, offset, allowedLength);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public long skip(long amount) throws IOException {
            if (amount <= 0) {
                return 0;
            }
            long remaining = maxBytes - bytesRead;
            if (remaining <= 0) {
                return read() < 0 ? 0 : 1;
            }
            long skipped = delegate.skip(Math.min(remaining, amount));
            bytesRead += skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(
                    delegate.available(),
                    Math.max(0, maxBytes - bytesRead)
            );
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(
                jakarta.servlet.ReadListener readListener
        ) {
            delegate.setReadListener(readListener);
        }

        private IOException tooLarge() {
            return new IOException(
                    "MCP tool request body exceeds configured limit"
            );
        }
    }
}
