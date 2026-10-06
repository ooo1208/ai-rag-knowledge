package io.github.ooo1208.trigger.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * MCP 执行入口在 MVC 反序列化前的请求体限制测试。
 */
class McpToolRequestSizeFilterTest {

    private static final String EXECUTE_PATH =
            "/api/v1/model-configs/demo/tools/read/execute";

    @Test
    void knownOversizedContentLengthReturns413() throws Exception {
        McpToolRequestSizeFilter filter = newFilter();
        MockHttpServletRequest request = request(EXECUTE_PATH, 5_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> fail("chain must not run")
        );

        assertEquals(413, response.getStatus());
    }

    @Test
    void chunkedBodyIsBoundedWhileReading() throws Exception {
        McpToolRequestSizeFilter filter = newFilter();
        MockHttpServletRequest request = chunkedRequest(EXECUTE_PATH, 5_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, ignoredResponse) -> {
            ServletInputStream input = servletRequest.getInputStream();
            while (input.read() >= 0) {
                // Drain until the bounded stream rejects the sixth KiB.
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
    }

    @Test
    void unrelatedPathKeepsOriginalRequest() throws Exception {
        McpToolRequestSizeFilter filter = newFilter();
        MockHttpServletRequest request = request(
                "/api/v1/model-configs/demo/tools",
                5_000
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};

        filter.doFilter(
                request,
                response,
                (actualRequest, ignoredResponse) -> {
                    called[0] = actualRequest == request;
                }
        );

        assertEquals(true, called[0]);
        assertEquals(200, response.getStatus());
    }

    private McpToolRequestSizeFilter newFilter() {
        return new McpToolRequestSizeFilter(4 * 1024);
    }

    private MockHttpServletRequest request(String path, int bytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContent("x".repeat(bytes).getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private MockHttpServletRequest chunkedRequest(String path, int bytes) {
        return new MockHttpServletRequest("POST", path) {
            {
                setContent("x".repeat(bytes).getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
    }
}
