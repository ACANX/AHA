package com.acanx.module.aha.core.security;

import com.acanx.module.aha.common.exception.ConfigException;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 密钥库工厂。
 *
 * <p>按 {@code Security.KeyStore} 选择实现、{@code Security.KeyStorePath} 决定路径。</p>
 *
 * @since 0.1.0
 */
public final class SecretStores {

    private static final Logger LOG = LoggerFactory.getLogger(SecretStores.class);

    /** 内置实现名：加密文件。 */
    public static final String ENCRYPTED_FILE = "encrypted-file";

    /** 默认密钥库文件名。 */
    private static final String DEFAULT_KEYSTORE_NAME = "Aha.keystore";

    private SecretStores() {
    }

    /**
     * 按配置创建密钥库。
     *
     * <p>创建失败（实现未知、缺少主密码、文件损坏等）时返回 {@code null}，
     * 由调用方退化为「不回退密钥库」而不是阻断启动——
     * 缺少主密码的环境仍应能用环境变量方式提供凭据。</p>
     *
     * @param config 配置，可为 {@code null}
     * @return 密钥库；不可用时返回 {@code null}
     */
    public static SecretStore create(AhaConfig config) {
        return create(config, null);
    }

    /**
     * 按配置创建密钥库，并可显式提供主密码。
     *
     * @param config         配置，可为 {@code null}
     * @param masterPassword 主密码；为 {@code null} 时读环境变量
     *                        {@link EncryptedFileSecretStore#MASTER_PASSWORD_ENV}
     * @return 密钥库；不可用时返回 {@code null}
     */
    public static SecretStore create(AhaConfig config, char[] masterPassword) {
        SecurityConfig security = config == null ? null : config.security();
        String kind = security == null || security.keyStore() == null || security.keyStore().isBlank()
                ? ENCRYPTED_FILE
                : security.keyStore().trim();
        if (!ENCRYPTED_FILE.equals(kind)) {
            // 未知实现属于配置错误，明确报出而不是静默降级
            throw new ConfigException("UNKNOWN_KEYSTORE", "不支持的密钥库实现: " + kind);
        }
        Path path = resolvePath(security);
        try {
            return masterPassword == null
                    ? new EncryptedFileSecretStore(path)
                    : new EncryptedFileSecretStore(path, masterPassword);
        } catch (RuntimeException e) {
            LOG.debug("密钥库不可用，将仅使用环境变量提供的凭据: {} ({})", path, e.getMessage());
            return null;
        }
    }

    /**
     * 解析密钥库路径。
     *
     * @param security 安全配置，可为 {@code null}
     * @return 绝对路径
     */
    private static Path resolvePath(SecurityConfig security) {
        String configured = security == null ? null : security.keyStorePath();
        if (configured != null && !configured.isBlank()) {
            return ConfigLoader.expandHome(configured).toAbsolutePath().normalize();
        }
        String home = System.getenv("AHA_HOME");
        Path base = home == null || home.isBlank()
                ? Path.of(System.getProperty("user.home"), ".aha")
                : Path.of(home);
        return base.resolve("Key").resolve(DEFAULT_KEYSTORE_NAME).toAbsolutePath().normalize();
    }
}
