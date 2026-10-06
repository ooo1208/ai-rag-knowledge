package io.github.ooo1208.infrastructure.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 解析模型目录中的外部凭证引用。
 *
 * <p>第一版只允许引用 Spring 配置属性，后续可以在这里接入 Secret Manager，
 * 不需要把密钥带入 application 层或数据库查询结果之外的展示对象。</p>
 */
@Component
public final class CredentialResolver {

    private static final String CONFIG_CREDENTIAL_PREFIX = "config:";

    private final Environment environment;

    public CredentialResolver(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    public String resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            throw new IllegalArgumentException(
                    "Model credentialRef must not be blank"
            );
        }

        String reference = credentialRef.trim();
        if (!reference.startsWith(CONFIG_CREDENTIAL_PREFIX)) {
            throw new IllegalArgumentException(
                    "Unsupported model credentialRef scheme"
            );
        }

        String propertyName = reference.substring(
                CONFIG_CREDENTIAL_PREFIX.length()
        ).trim();
        if (propertyName.isBlank()) {
            throw new IllegalArgumentException(
                    "Model credentialRef property must not be blank"
            );
        }

        String credential = environment.getProperty(propertyName);
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException(
                    "Model credential is not configured: " + propertyName
            );
        }
        return credential;
    }
}
