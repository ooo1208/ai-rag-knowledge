package io.github.ooo1208.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.domain.mcp.McpTransportType;
import io.github.ooo1208.infrastructure.config.CredentialResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP SSE 适配器在真正连接 Provider 前的安全策略测试。
 */
class SpringAiMcpToolExecutionAdapterTest {

    @Test
    void disabledAdapterRejectsBeforeAnyOutboundValidation() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(false);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-web",
                                McpTransportType.SSE,
                                "http://127.0.0.1:8080/sse",
                                null
                        ),
                        "web_search",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.UNAVAILABLE,
                exception.kind()
        );
    }

    @Test
    void firstAdapterRejectsUnsupportedTransport() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(true);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-stdio",
                                McpTransportType.STDIO,
                                null,
                                null
                        ),
                        "read_file",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.POLICY,
                exception.kind()
        );
    }

    @Test
    void enabledAdapterRejectsPrivateEndpointBeforeConnecting() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(true);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-private",
                                McpTransportType.SSE,
                                "http://127.0.0.1:8080/sse",
                                null
                        ),
                        "web_search",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.POLICY,
                exception.kind()
        );
    }

    @Test
    void enabledAdapterRejectsMissingCredentialBeforeConnecting() {
        SpringAiMcpToolExecutionAdapter adapter = newAdapter(true);

        McpToolExecutionException exception = assertThrows(
                McpToolExecutionException.class,
                () -> adapter.execute(
                        new McpServerConnectionDescriptor(
                                "server-public",
                                McpTransportType.SSE,
                                "https://example.com/mcp",
                                "config:missing.mcp.key"
                        ),
                        "web_search",
                        Map.of()
                )
        );

        assertEquals(
                McpToolExecutionException.Kind.UNAVAILABLE,
                exception.kind()
        );
    }

    @Test
    void enabledAdapterRequiresHostAllowlistAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpringAiMcpToolExecutionAdapter(
                        true,
                        1_000,
                        new CredentialResolver(new MockEnvironment()),
                        new ObjectMapper(),
                        ""
                )
        );
    }

    @Test
    void rawSseEventLimitCancelsBeforeForwardingToSdkParser() {
        RecordingBodySubscriber delegate = new RecordingBodySubscriber();
        SpringAiMcpToolExecutionAdapter.CappedBodySubscriber<String> capped =
                new SpringAiMcpToolExecutionAdapter.CappedBodySubscriber<>(
                        delegate,
                        8
                );
        RecordingSubscription subscription = new RecordingSubscription();
        capped.onSubscribe(subscription);

        capped.onNext(List.of(ByteBuffer.wrap(
                "data:xxx\n\n".getBytes(StandardCharsets.UTF_8)
        )));

        assertTrue(delegate.body.isCompletedExceptionally());
        assertTrue(subscription.cancelled);
        assertEquals(0, delegate.forwardedBuffers);
    }

    @Test
    void rawSseEventLimitResetsAfterBlankEventLine() {
        RecordingBodySubscriber delegate = new RecordingBodySubscriber();
        SpringAiMcpToolExecutionAdapter.CappedBodySubscriber<String> capped =
                new SpringAiMcpToolExecutionAdapter.CappedBodySubscriber<>(
                        delegate,
                        8
                );
        capped.onSubscribe(new RecordingSubscription());

        capped.onNext(List.of(ByteBuffer.wrap(
                "x\n\ny\n\n".getBytes(StandardCharsets.UTF_8)
        )));

        assertFalse(delegate.body.isCompletedExceptionally());
        assertEquals(1, delegate.forwardedBuffers);
    }

    private SpringAiMcpToolExecutionAdapter newAdapter(boolean enabled) {
        return new SpringAiMcpToolExecutionAdapter(
                enabled,
                1_000,
                new CredentialResolver(new MockEnvironment()),
                new ObjectMapper(),
                "example.com"
        );
    }

    private static final class RecordingBodySubscriber
            implements java.net.http.HttpResponse.BodySubscriber<String> {

        private final CompletableFuture<String> body = new CompletableFuture<>();
        private int forwardedBuffers;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            forwardedBuffers++;
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete("ok");
        }

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }
    }

    private static final class RecordingSubscription
            implements Flow.Subscription {

        private boolean cancelled;

        @Override
        public void request(long count) {
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
