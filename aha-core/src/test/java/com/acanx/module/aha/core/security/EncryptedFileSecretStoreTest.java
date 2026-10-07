package com.acanx.module.aha.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EncryptedFileSecretStore} 测试。
 *
 * @since 0.1.0
 */
class EncryptedFileSecretStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndRetrievesSecret() {
        Path keyStore = tempDir.resolve("Aha.keystore");
        EncryptedFileSecretStore store = new EncryptedFileSecretStore(keyStore, "s3cret".toCharArray());

        store.store("AHA_API_KEY_OPENAI", "sk-test-123".toCharArray());

        assertThat(store.retrieve("AHA_API_KEY_OPENAI")).containsExactly("sk-test-123".toCharArray());
    }

    @Test
    void doesNotPersistPlaintext() throws Exception {
        Path keyStore = tempDir.resolve("Aha.keystore");
        EncryptedFileSecretStore store = new EncryptedFileSecretStore(keyStore, "pw".toCharArray());
        store.store("k", "top-secret-value".toCharArray());

        String content = Files.readString(keyStore);
        assertThat(content).doesNotContain("top-secret-value");
    }

    @Test
    void deletesSecret() {
        Path keyStore = tempDir.resolve("Aha.keystore");
        EncryptedFileSecretStore store = new EncryptedFileSecretStore(keyStore, "pw".toCharArray());
        store.store("k", "v".toCharArray());

        store.delete("k");

        assertThat(store.retrieve("k")).isNull();
    }

    @Test
    void wrongPasswordCannotDecrypt() {
        Path keyStore = tempDir.resolve("Aha.keystore");
        new EncryptedFileSecretStore(keyStore, "right".toCharArray())
                .store("k", "v".toCharArray());

        EncryptedFileSecretStore wrong = new EncryptedFileSecretStore(keyStore, "wrong".toCharArray());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> wrong.retrieve("k"))
                .isInstanceOf(com.acanx.module.aha.common.exception.SecurityException.class);
    }
}
