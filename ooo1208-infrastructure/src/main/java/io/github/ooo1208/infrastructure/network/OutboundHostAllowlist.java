package io.github.ooo1208.infrastructure.network;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出站连接使用的显式 host 白名单。
 *
 * <p>支持精确主机名/IP 和 {@code *.example.com} 子域模式。通配符只匹配
 * 子域，不会匹配根域本身；白名单不会改变 URL 的 DNS 解析策略，调用方仍
 * 需要先交给 {@link OutboundUrlValidator} 做地址和端口检查。</p>
 */
public final class OutboundHostAllowlist {

    private final Set<String> patterns;

    private OutboundHostAllowlist(Collection<String> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        this.patterns = patterns.stream()
                .map(pattern -> pattern == null ? "" : pattern.trim())
                .filter(pattern -> !pattern.isBlank())
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .peek(OutboundHostAllowlist::validatePattern)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 从逗号分隔配置解析白名单。空配置得到空白名单，并由调用方决定是否
     * 在启用出站能力时拒绝启动或请求。
     */
    public static OutboundHostAllowlist fromCsv(String rawPatterns) {
        if (rawPatterns == null || rawPatterns.isBlank()) {
            return new OutboundHostAllowlist(Set.of());
        }
        return new OutboundHostAllowlist(
                Arrays.asList(rawPatterns.split(","))
        );
    }

    public static OutboundHostAllowlist of(String... patterns) {
        return new OutboundHostAllowlist(Arrays.asList(patterns));
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public boolean matches(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return false;
        }
        String host = rawHost.toLowerCase(Locale.ROOT);
        return patterns.stream().anyMatch(pattern -> {
            if (!pattern.startsWith("*.")) {
                return host.equals(pattern);
            }
            String suffix = pattern.substring(1);
            return host.endsWith(suffix)
                    && !host.equals(suffix.substring(1));
        });
    }

    private static void validatePattern(String pattern) {
        if (pattern.length() < 1
                || pattern.length() > 253
                || pattern.indexOf('/') >= 0
                || pattern.indexOf(':') >= 0
                || pattern.chars().anyMatch(Character::isWhitespace)
                || pattern.chars().anyMatch(Character::isISOControl)
                || pattern.indexOf('*') >= 0
                && (!pattern.startsWith("*.")
                || pattern.length() <= 2
                || pattern.indexOf('*', 1) >= 0)) {
            throw new IllegalArgumentException(
                    "outbound host allowlist contains an invalid pattern"
            );
        }
    }
}
