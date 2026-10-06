package io.github.ooo1208.infrastructure.network;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 出站 URL 校验的本地单元测试。
 *
 * <p>测试只使用 IP 字面量，不依赖公网 DNS 或第三方服务，避免把网络状态
 * 变成构建的前置条件。</p>
 */
class OutboundUrlValidatorTest {

    @Test
    void publicInternetRejectsLoopback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboundUrlValidator.validatePublicInternet(
                        "http://127.0.0.1"
                )
        );
    }

    @Test
    void publicInternetRejectsPrivateAddress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboundUrlValidator.validatePublicInternet(
                        "https://10.0.0.8"
                )
        );
    }

    @Test
    void publicInternetRejectsUnsupportedScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboundUrlValidator.validatePublicInternet(
                        "file:///etc/passwd"
                )
        );
    }

    @Test
    void publicInternetRejectsMissingScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboundUrlValidator.validatePublicInternet(
                        "example.com"
                )
        );
    }

    @Test
    void publicInternetRejectsIpv6DocumentationRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboundUrlValidator.validatePublicInternet(
                        "https://[2001:db8::1]"
                )
        );
    }

    @Test
    void publicInternetRejectsQueryAndNonStandardPort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutboundUrlValidator.validatePublicInternet(
                        "https://example.com:8443/search?q=rag"
                )
        );
    }

    @Test
    void managedProviderMustOptInBeforeLoopbackIsAllowed() {
        OutboundUrlValidator.OutboundUrlPolicy policy =
                OutboundUrlValidator.OutboundUrlPolicy.managedProvider(
                        Set.of(11_434)
                );

        OutboundUrlValidator.ValidatedUrl validated =
                OutboundUrlValidator.validate(
                        "http://127.0.0.1:11434",
                        policy
                );

        assertEquals("http://127.0.0.1:11434", validated.value());
    }
}
