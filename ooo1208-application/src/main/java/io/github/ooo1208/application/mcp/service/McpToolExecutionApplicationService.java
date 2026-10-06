package io.github.ooo1208.application.mcp.service;

import io.github.ooo1208.application.mcp.exception.McpToolExecutionException;
import io.github.ooo1208.application.mcp.model.McpServerConnectionDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolDescriptor;
import io.github.ooo1208.application.mcp.model.McpToolExecutionResult;
import io.github.ooo1208.application.mcp.port.in.ExecuteMcpToolUseCase;
import io.github.ooo1208.application.mcp.port.out.McpServerConnectionQueryPort;
import io.github.ooo1208.application.mcp.port.out.McpToolExecutionPort;
import io.github.ooo1208.domain.modelcatalog.ModelConfigId;

import java.lang.reflect.Array;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MCP 工具执行策略编排器。
 *
 * <p>执行前再次按模型预设白名单解析工具，避免调用方绕过选择接口直接提交
 * serverId、endpoint 或任意工具名。第一版只允许只读且无需确认的工具，写工具
 * 必须等审批/审计链路接入后再开放。</p>
 */
public final class McpToolExecutionApplicationService
        implements ExecuteMcpToolUseCase {

    private static final int MAX_ARGUMENTS = 32;
    private static final int MAX_ARGUMENT_KEY_LENGTH = 100;
    private static final int MAX_ARGUMENT_DEPTH = 6;
    private static final int MAX_ARGUMENT_NODES = 256;
    private static final int MAX_ARGUMENT_STRING_LENGTH = 8_192;
    private static final int MAX_ARGUMENT_CHARS = 64 * 1024;
    private static final Duration CALL_WINDOW = Duration.ofMinutes(1);

    private final McpToolCatalogApplicationService catalogService;
    private final McpServerConnectionQueryPort serverQueryPort;
    private final McpToolExecutionPort executionPort;
    private final Clock clock;
    private final ConcurrentMap<ExecutionKey, CallWindow> callWindows =
            new ConcurrentHashMap<>();

    public McpToolExecutionApplicationService(
            McpToolCatalogApplicationService catalogService,
            McpServerConnectionQueryPort serverQueryPort,
            McpToolExecutionPort executionPort
    ) {
        this(
                catalogService,
                serverQueryPort,
                executionPort,
                Clock.systemUTC()
        );
    }

    public McpToolExecutionApplicationService(
            McpToolCatalogApplicationService catalogService,
            McpServerConnectionQueryPort serverQueryPort,
            McpToolExecutionPort executionPort,
            Clock clock
    ) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.serverQueryPort = Objects.requireNonNull(serverQueryPort);
        this.executionPort = Objects.requireNonNull(executionPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public McpToolExecutionResult execute(
            ModelConfigId modelConfigId,
            String toolId,
            Map<String, Object> arguments
    ) {
        Objects.requireNonNull(modelConfigId, "modelConfigId");
        if (toolId == null || toolId.isBlank()) {
            throw new McpToolExecutionException(
                    "toolId must not be blank"
            );
        }

        McpToolDescriptor tool = resolveTool(
                modelConfigId,
                toolId.trim()
        );
        if (!tool.readOnly() || tool.requiresConfirmation()) {
            throw new McpToolExecutionException(
                    "MCP tool requires explicit confirmation and is not executable yet"
            );
        }

        Map<String, Object> safeArguments = validateArguments(arguments);
        McpServerConnectionDescriptor server;
        try {
            server = serverQueryPort.queryEnabledServer(tool.serverId());
        } catch (McpToolExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new McpToolExecutionException(
                    "MCP server connection is unavailable",
                    exception,
                    McpToolExecutionException.Kind.UNAVAILABLE
            );
        }
        if (server == null) {
            throw new McpToolExecutionException(
                    "MCP server connection is not enabled",
                    McpToolExecutionException.Kind.UNAVAILABLE
            );
        }

        // 只对已经通过参数校验且解析到启用服务器的实际调用计预算。
        acquireCallBudget(modelConfigId, tool);
        try {
            return Objects.requireNonNull(
                    executionPort.execute(server, tool.name(), safeArguments),
                    "MCP execution result must not be null"
            );
        } catch (McpToolExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new McpToolExecutionException(
                    "MCP provider tool execution failed",
                    exception,
                    McpToolExecutionException.Kind.PROVIDER
            );
        }
    }

    private void acquireCallBudget(
            ModelConfigId modelConfigId,
            McpToolDescriptor tool
    ) {
        ExecutionKey key = new ExecutionKey(modelConfigId.value(), tool.toolId());
        Instant now = clock.instant();
        try {
            callWindows.compute(key, (ignored, previous) -> {
                if (previous == null
                        || !now.isBefore(previous.startedAt().plus(CALL_WINDOW))) {
                    return new CallWindow(now, 1);
                }
                if (previous.count() >= tool.maxCalls()) {
                    throw new CallBudgetExceeded();
                }
                return new CallWindow(previous.startedAt(), previous.count() + 1);
            });
        } catch (CallBudgetExceeded exception) {
            throw new McpToolExecutionException(
                    "MCP tool call limit reached for the current minute",
                    McpToolExecutionException.Kind.RATE_LIMITED
            );
        }
    }

    private McpToolDescriptor resolveTool(
            ModelConfigId modelConfigId,
            String toolId
    ) {
        List<McpToolDescriptor> selected;
        try {
            selected = catalogService.select(
                    modelConfigId,
                    List.of(toolId)
            );
        } catch (RuntimeException exception) {
            if (exception instanceof McpToolExecutionException) {
                throw (McpToolExecutionException) exception;
            }
            throw new McpToolExecutionException(
                    "MCP tool is not enabled for the model configuration",
                    exception
            );
        }

        if (selected.size() != 1) {
            throw new McpToolExecutionException(
                    "MCP tool is not enabled for the model configuration"
            );
        }
        return selected.get(0);
    }

    private Map<String, Object> validateArguments(
            Map<String, Object> arguments
    ) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        if (arguments.size() > MAX_ARGUMENTS) {
            throw new McpToolExecutionException(
                    "too many MCP tool arguments"
            );
        }
        ArgumentBudget budget = new ArgumentBudget();
        return copyMap(arguments, 0, budget);
    }

    private Map<String, Object> copyMap(
            Map<?, ?> source,
            int depth,
            ArgumentBudget budget
    ) {
        if (depth > MAX_ARGUMENT_DEPTH || source.size() > MAX_ARGUMENTS) {
            throw new McpToolExecutionException(
                    "MCP tool arguments are too deeply nested or too large"
            );
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = validateKey(entry.getKey(), budget);
            copy.put(key, copyValue(entry.getValue(), depth + 1, budget));
        }
        return Collections.unmodifiableMap(copy);
    }

    private String validateKey(Object rawKey, ArgumentBudget budget) {
        if (!(rawKey instanceof String key)
                || key.isBlank()
                || key.length() > MAX_ARGUMENT_KEY_LENGTH
                || key.chars().anyMatch(Character::isISOControl)) {
            throw new McpToolExecutionException(
                    "MCP tool argument name is invalid"
            );
        }
        budget.addCharacters(key.length());
        return key;
    }

    private Object copyValue(
            Object value,
            int depth,
            ArgumentBudget budget
    ) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_ARGUMENT_DEPTH) {
            throw new McpToolExecutionException(
                    "MCP tool arguments are too deeply nested"
            );
        }
        budget.addNode();
        if (value instanceof String string) {
            if (string.length() > MAX_ARGUMENT_STRING_LENGTH) {
                throw new McpToolExecutionException(
                        "MCP tool argument value is too long"
                );
            }
            budget.addCharacters(string.length());
            return string;
        }
        if (value instanceof Number || value instanceof Boolean) {
            budget.addCharacters(value.toString().length());
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map, depth, budget);
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > MAX_ARGUMENTS) {
                throw new McpToolExecutionException(
                        "MCP tool argument collection is too large"
                );
            }
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(copyValue(item, depth + 1, budget));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length > MAX_ARGUMENTS) {
                throw new McpToolExecutionException(
                        "MCP tool argument array is too large"
                );
            }
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(copyValue(Array.get(value, index), depth + 1, budget));
            }
            return Collections.unmodifiableList(copy);
        }
        throw new McpToolExecutionException(
                "MCP tool argument value type is not supported"
        );
    }

    private record ExecutionKey(String modelConfigId, String toolId) {
    }

    private record CallWindow(Instant startedAt, int count) {
    }

    private static final class CallBudgetExceeded extends RuntimeException {
    }

    private static final class ArgumentBudget {

        private int nodes;
        private int characters;

        private void addNode() {
            if (++nodes > MAX_ARGUMENT_NODES) {
                throw new McpToolExecutionException(
                        "MCP tool arguments contain too many values"
                );
            }
        }

        private void addCharacters(int amount) {
            if (amount > MAX_ARGUMENT_CHARS - characters) {
                throw new McpToolExecutionException(
                        "MCP tool arguments exceed the size limit"
                );
            }
            characters += amount;
        }
    }
}
