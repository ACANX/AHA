package com.acanx.module.aha.core.security;

import com.acanx.module.aha.common.exception.ConfigException;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密钥库接入测试。
 *
 * @since 0.1.0
 */
class SecretStoresTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearResolver() {
        // ConfigLoader 的解析器是静态的，避免用例间相互影响
        ConfigLoader.setSecretResolver(null);
    }

    @Test
    void defaultKindIsEncryptedFile() {
        AhaConfig config = withSecurity(null, keyStorePath());

        assertThat(SecretStores.create(config, "pw".toCharArray()))
                .isInstanceOf(EncryptedFileSecretStore.class);
    }

    @Test
    void unknownKindIsRejected() {
        // 未知实现属配置错误，应明确报出而非静默降级
        AhaConfig config = withSecurity("keychain", keyStorePath());

        assertThatThrownBy(() -> SecretStores.create(config, "pw".toCharArray()))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("keychain");
    }

    @Test
    void missingMasterPasswordYieldsNullInsteadOfCrashing() {
        // 未设 AHA_MASTER_PASSWORD 时不应阻断启动：仍可用环境变量提供凭据
        AhaConfig config = withSecurity("encrypted-file", keyStorePath());

        assertThat(SecretStores.create(config)).isNull();
    }

    @Test
    void configuredPathIsUsed() {
        Path custom = tempDir.resolve("nested/k.keystore");
        AhaConfig config = withSecurity("encrypted-file", custom);

        SecretStore store = SecretStores.create(config, "pw".toCharArray());

        assertThat(store).isInstanceOf(EncryptedFileSecretStore.class);
        assertThat(((EncryptedFileSecretStore) store).keyStorePath())
                .isEqualTo(custom.toAbsolutePath().normalize());
    }

    @Test
    void placeholderFallsBackToSecretStoreWhenEnvMissing() {
        // 核心契约：环境变量未命中 → 查密钥库
        ConfigLoader.setSecretResolver(key -> "AHA_TEST_KEY".equals(key) ? "sk-from-keystore".toCharArray() : null);

        assertThat(ConfigLoader.resolveSecrets("${AHA_TEST_KEY}")).isEqualTo("sk-from-keystore");
    }

    @Test
    void placeholderStaysIntactWhenNeitherSourceHasIt() {
        ConfigLoader.setSecretResolver(key -> null);

        // 保留占位符而非静默替换为空串，便于诊断
        assertThat(ConfigLoader.resolveSecrets("${AHA_MISSING_KEY}")).isEqualTo("${AHA_MISSING_KEY}");
    }

    @Test
    void failingSecretStoreDoesNotBreakResolution() {
        // 密钥库损坏/不可读时不应让配置解析崩溃
        ConfigLoader.setSecretResolver(key -> {
            throw new com.acanx.module.aha.common.exception.SecurityException(
                    "DECRYPT_FAILED", "boom");
        });

        assertThat(ConfigLoader.resolveSecrets("${AHA_X}")).isEqualTo("${AHA_X}");
    }

    @Test
    void plainValueIsReturnedUnchanged() {
        ConfigLoader.setSecretResolver(key -> "should-not-be-used".toCharArray());

        assertThat(ConfigLoader.resolveSecrets("sk-plain")).isEqualTo("sk-plain");
        assertThat(ConfigLoader.resolveSecrets(null)).isNull();
    }

    private Path keyStorePath() {
        return tempDir.resolve("Aha.keystore");
    }

    private static AhaConfig withSecurity(String kind, Path path) {
        return new AhaConfig(null, null, null,
                new SecurityConfig(kind, path.toString()), null, null, null);
    }
}
