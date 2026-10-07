package com.acanx.module.aha.core.security;

/**
 * 密钥存储。
 *
 * @since 0.1.0
 */
public interface SecretStore {

    /**
     * 存储密钥。
     *
     * @param key    键
     * @param secret 密钥
     */
    void store(String key, char[] secret);

    /**
     * 读取密钥。
     *
     * @param key 键
     * @return 密钥
     */
    char[] retrieve(String key);

    /**
     * 删除密钥。
     *
     * @param key 键
     */
    void delete(String key);
}
