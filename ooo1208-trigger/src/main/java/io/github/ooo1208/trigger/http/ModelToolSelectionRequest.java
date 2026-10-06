package io.github.ooo1208.trigger.http;

import java.util.List;

/**
 * MCP 工具选择请求，只允许提交稳定 toolId 集合。
 */
public record ModelToolSelectionRequest(List<String> toolIds) {
}
