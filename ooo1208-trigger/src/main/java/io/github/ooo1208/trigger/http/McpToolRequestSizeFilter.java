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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 在 JSON 反序列化前限制 MCP 工具请求体大小。
 *
 * <p>application 层还会限制参数结构和总字符预算，但那些检查发生在
 * MVC 已经读取请求体之后；这里先挡住 Content-Length 明确过大的请求，
 * 并对 chunked 请求提供同样的读取上限。</p>
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
            response.sendError(
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "MCP tool request body is too large"
            );
            return;
        }
        try {
            filterChain.doFilter(
                    new BoundedRequest(request, maxRequestBytes),
                    response
            );
        } catch (IOException exception) {
            if (!response.isCommitted()
                    && exception.getMessage() != null
                    && exception.getMessage().contains("exceeds configured limit")) {
                response.sendError(
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "MCP tool request body is too large"
                );
                return;
            }
            throw exception;
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
