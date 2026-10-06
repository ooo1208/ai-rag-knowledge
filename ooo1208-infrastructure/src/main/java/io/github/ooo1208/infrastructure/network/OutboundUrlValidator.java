package io.github.ooo1208.infrastructure.network;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * 校验由基础设施层发起的 HTTP 出站地址。
 *
 * <p>联网搜索、远程 MCP 和管理员配置的模型服务都会产生“服务端代替用户
 * 访问一个地址”的场景。任何一个场景都不能把请求中的任意 URL 直接交给
 * {@code RestClient}。这个类提供一个无状态的最小安全边界：</p>
 *
 * <ul>
 *     <li>只允许 HTTP/HTTPS，拒绝 URI userinfo、query 和 fragment；</li>
 *     <li>校验端口白名单，公网策略默认只允许 80 和 443；</li>
 *     <li>解析域名后检查所有 A/AAAA 记录，阻止回环、私网、链路本地、
 *     CGNAT、云元数据和组播地址；</li>
 *     <li>返回规范化的 {@link URI}，调用方应继续使用这个返回值，避免把
 *     原始字符串重新交给 HTTP 客户端。</li>
 * </ul>
 *
 * <p>DNS 解析和实际连接之间仍然存在极短的 TOCTOU 窗口，因此这不是完整的
 * 网络隔离方案。生产环境还应在 egress 防火墙、代理或 Service Mesh 层重复
 * 限制，并给 MCP/搜索连接增加显式 host allowlist。</p>
 */
public final class OutboundUrlValidator {

    private static final int MAX_URL_LENGTH = 2_048;
    private static final Set<Integer> PUBLIC_PORTS = Set.of(80, 443);

    /**
     * 公网出站策略：不允许私网地址，只允许标准 HTTP/HTTPS 端口。
     */
    public static final OutboundUrlPolicy PUBLIC_INTERNET =
            new OutboundUrlPolicy(false, PUBLIC_PORTS);

    private OutboundUrlValidator() {
    }

    /**
     * 按公网策略校验地址。
     *
     * @param rawUrl 未信任的 URL 字符串
     * @return 已解析并通过 DNS 地址检查的 URL
     */
    public static ValidatedUrl validatePublicInternet(String rawUrl) {
        return validate(rawUrl, PUBLIC_INTERNET);
    }

    /**
     * 校验管理员登记的模型 Provider 地址。
     *
     * <p>与公网搜索/MCP 不同，模型 Provider 可以是内网或本机服务，但仍
     * 必须同时满足显式 host 白名单、端口白名单和 DNS 地址策略。</p>
     */
    public static ValidatedUrl validateManagedProvider(
            String rawUrl,
            OutboundHostAllowlist allowedHosts,
            Set<Integer> allowedPorts
    ) {
        return validateManagedProvider(
                rawUrl,
                allowedHosts,
                allowedPorts,
                true
        );
    }

    /**
     * 按管理员登记的 host、端口和私网开关校验模型 Provider 地址。
     */
    public static ValidatedUrl validateManagedProvider(
            String rawUrl,
            OutboundHostAllowlist allowedHosts,
            Set<Integer> allowedPorts,
            boolean allowPrivateAddresses
    ) {
        Objects.requireNonNull(allowedHosts, "allowedHosts");
        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException(
                    "managed provider requires at least one allowed host"
            );
        }
        ValidatedUrl validated = validate(
                rawUrl,
                new OutboundUrlPolicy(
                        allowPrivateAddresses,
                        allowedPorts
                )
        );
        if (!allowedHosts.matches(validated.uri().getHost())) {
            throw new IllegalArgumentException(
                    "managed provider host is not in the configured allowlist"
            );
        }
        return validated;
    }

    /**
     * 按调用方显式指定的策略校验地址。
     *
     * <p>允许私网地址的策略只能用于已经由管理员登记、且不接受用户输入的
     * 内部 Provider。它不应作为联网搜索或远程 MCP 的默认策略。</p>
     */
    public static ValidatedUrl validate(
            String rawUrl,
            OutboundUrlPolicy policy
    ) {
        Objects.requireNonNull(policy, "policy");

        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("outbound URL must not be blank");
        }
        if (rawUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "outbound URL exceeds the maximum length"
            );
        }
        if (rawUrl.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "outbound URL must not contain control characters"
            );
        }

        URI uri = parse(rawUrl.trim());
        if (uri.getScheme() == null || uri.getScheme().isBlank()) {
            throw new IllegalArgumentException(
                    "outbound URL must include a scheme"
            );
        }
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(
                    "only http and https outbound URLs are supported"
            );
        }
        if (uri.isOpaque() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "outbound URL must be a hierarchical URL without userinfo"
            );
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "outbound URL must not contain query or fragment"
            );
        }

        String host = uri.getHost();
        if (host == null || host.isBlank() || host.indexOf('%') >= 0) {
            throw new IllegalArgumentException(
                    "outbound URL must contain a valid DNS host"
            );
        }

        int port = uri.getPort();
        int effectivePort = port < 0
                ? (scheme.equals("https") ? 443 : 80)
                : port;
        if (effectivePort < 1 || effectivePort > 65_535
                || !policy.allowedPorts().contains(effectivePort)) {
            throw new IllegalArgumentException(
                    "outbound URL port is not allowed: " + effectivePort
            );
        }

        verifyResolvedAddresses(host, policy);

        try {
            // Rebuild with the lower-case scheme/host to avoid callers using a
            // visually different URL after validation. Keep the original path.
            URI normalized = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host.toLowerCase(java.util.Locale.ROOT),
                    port,
                    uri.getPath(),
                    null,
                    null
            );
            return new ValidatedUrl(normalized);
        } catch (URISyntaxException exception) {
            // The URI was already parsed above; this indicates a malformed
            // host/path combination after normalization.
            throw new IllegalArgumentException(
                    "outbound URL cannot be normalized", exception
            );
        }
    }

    private static URI parse(String rawUrl) {
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "outbound URL is malformed", exception
            );
        }
    }

    private static void verifyResolvedAddresses(
            String host,
            OutboundUrlPolicy policy
    ) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "outbound URL host cannot be resolved", exception
            );
        }

        if (addresses.length == 0) {
            throw new IllegalArgumentException(
                    "outbound URL host has no resolved address"
            );
        }

        if (!policy.allowPrivateAddresses()
                && Arrays.stream(addresses).anyMatch(
                OutboundUrlValidator::isRestrictedAddress
        )) {
            throw new IllegalArgumentException(
                    "outbound URL resolves to a private or reserved address"
            );
        }
    }

    private static boolean isRestrictedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = unsigned(bytes[0]);
            int second = unsigned(bytes[1]);

            // Unspecified, RFC1918, CGNAT, benchmarking and TEST-NET ranges.
            return first == 0
                    || first == 10
                    || first == 100 && second >= 64 && second <= 127
                    || first == 172 && second >= 16 && second <= 31
                    || first == 192 && second == 0
                    || first == 192 && second == 168
                    || first == 198 && (second == 18 || second == 19)
                    || first == 198 && second == 51
                    || first == 203 && second == 0
                    || first >= 224;
        }

        if (bytes.length == 16) {
            // IPv4-mapped IPv6 address: inspect the embedded IPv4 address too.
            boolean mapped = true;
            for (int index = 0; index < 10; index++) {
                if (bytes[index] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && bytes[10] == (byte) 0xff
                    && bytes[11] == (byte) 0xff) {
                return isRestrictedAddress(
                        toIpv4(bytes[12], bytes[13], bytes[14], bytes[15])
                );
            }

            int first = unsigned(bytes[0]);
            int second = unsigned(bytes[1]);
            // fc00::/7 unique-local, fe80::/10 link-local and ::/128.
            return first == 0
                    || (first & 0xfe) == 0xfc
                    || first == 0xfe && (second & 0xc0) == 0x80
                    || first == 0x20 && second == 0x01
                    && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8;
        }

        return false;
    }

    private static InetAddress toIpv4(
            byte first,
            byte second,
            byte third,
            byte fourth
    ) {
        try {
            return InetAddress.getByAddress(
                    new byte[]{first, second, third, fourth}
            );
        } catch (Exception exception) {
            // Four bytes are always a valid IPv4 address; keep a defensive
            // fallback so validation still fails closed if the JDK changes.
            throw new IllegalArgumentException(
                    "invalid IPv4-mapped address", exception
            );
        }
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    /**
     * Explicit egress policy. Keep the set immutable so a shared policy cannot
     * be widened accidentally by a request handler.
     */
    public record OutboundUrlPolicy(
            boolean allowPrivateAddresses,
            Set<Integer> allowedPorts
    ) {
        public OutboundUrlPolicy {
            if (allowedPorts == null || allowedPorts.isEmpty()) {
                throw new IllegalArgumentException(
                        "allowedPorts must not be empty"
                );
            }
            if (allowedPorts.stream().anyMatch(
                    port -> port == null || port < 1 || port > 65_535
            )) {
                throw new IllegalArgumentException(
                        "allowedPorts must contain valid TCP ports"
                );
            }
            allowedPorts = Set.copyOf(allowedPorts);
        }

        /**
         * 签名明确的内部 Provider 策略。仅供管理员登记的服务地址使用。
         */
        public static OutboundUrlPolicy managedProvider(
                Set<Integer> allowedPorts
        ) {
            return new OutboundUrlPolicy(true, allowedPorts);
        }
    }

    /**
     * 通过校验后的 URL 包装，避免下游误用未经校验的字符串。
     */
    public record ValidatedUrl(URI uri) {
        public ValidatedUrl {
            Objects.requireNonNull(uri, "uri");
        }

        public String value() {
            return uri.toString();
        }
    }
}
