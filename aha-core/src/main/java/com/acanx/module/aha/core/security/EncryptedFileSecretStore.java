package com.acanx.module.aha.core.security;

import com.acanx.module.aha.common.exception.SecurityException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 基于加密文件的密钥存储。
 *
 * <p>AES-256-GCM 加密，PBKDF2WithHmacSHA256 派生密钥。
 * 文件为 JSON，条目格式为 {@code base64(salt)}:{@code base64(iv)}:{@code base64(ciphertext)}。</p>
 *
 * <p>主密码来源：构造参数，或环境变量 {@code AHA_MASTER_PASSWORD}。</p>
 *
 * @since 0.1.0
 */
public final class EncryptedFileSecretStore implements SecretStore {

    /** 主密码环境变量名。 */
    public static final String MASTER_PASSWORD_ENV = "AHA_MASTER_PASSWORD";

    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final Path keyStorePath;
    private final char[] masterPassword;
    private final SecureRandom random = new SecureRandom();

    /**
     * 构造密钥存储，主密码取自环境变量 {@code AHA_MASTER_PASSWORD}。
     *
     * @param keyStorePath 密钥库路径
     */
    public EncryptedFileSecretStore(Path keyStorePath) {
        this(keyStorePath, resolvePasswordFromEnv());
    }

    /**
     * 构造密钥存储。
     *
     * @param keyStorePath   密钥库路径
     * @param masterPassword 主密码
     */
    public EncryptedFileSecretStore(Path keyStorePath, char[] masterPassword) {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new SecurityException("MASTER_PASSWORD_MISSING", "主密码不能为空");
        }
        this.keyStorePath = keyStorePath;
        this.masterPassword = masterPassword.clone();
    }

    /**
     * 密钥库路径。
     *
     * @return 路径
     */
    public Path keyStorePath() {
        return keyStorePath;
    }

    @Override
    public synchronized void store(String key, char[] secret) {
        ObjectNode root = readStore();
        ObjectNode entries = root.withObjectProperty("entries");
        try {
            entries.put(key, encrypt(secret));
        } catch (GeneralSecurityException e) {
            throw new SecurityException("SECRET_ENCRYPT_FAILED", "加密失败: " + key, e);
        }
        writeStore(root);
    }

    @Override
    public synchronized char[] retrieve(String key) {
        ObjectNode root = readStore();
        JsonNode value = root.path("entries").path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return decrypt(value.asString());
        } catch (GeneralSecurityException e) {
            throw new SecurityException("SECRET_DECRYPT_FAILED", "解密失败: " + key, e);
        }
    }

    @Override
    public synchronized void delete(String key) {
        ObjectNode root = readStore();
        JsonNode entries = root.path("entries");
        if (entries.isObject()) {
            ((ObjectNode) entries).remove(key);
        }
        writeStore(root);
    }

    /**
     * 列出全部密钥名（不返回值）。
     *
     * @return 键名列表，按键名排序；密钥库不存在时为空列表
     */
    public synchronized List<String> listKeys() {
        JsonNode entries = readStore().path("entries");
        if (!entries.isObject()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        entries.properties().forEach(entry -> names.add(entry.getKey()));
        names.sort(java.util.Comparator.naturalOrder());
        return names;
    }

    // ------------------------------------------------------------------

    private String encrypt(char[] secret) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        SecretKey key = deriveKey(salt);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(new String(secret).getBytes(StandardCharsets.UTF_8));

        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(iv) + ":"
                + encoder.encodeToString(ciphertext);
    }

    private char[] decrypt(String payload) throws GeneralSecurityException {
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            throw new GeneralSecurityException("非法的密钥条目格式");
        }
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt = decoder.decode(parts[0]);
        byte[] iv = decoder.decode(parts[1]);
        byte[] ciphertext = decoder.decode(parts[2]);

        SecretKey key = deriveKey(salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, StandardCharsets.UTF_8).toCharArray();
    }

    private SecretKey deriveKey(byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_LENGTH);
        try {
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private ObjectNode readStore() {
        if (!Files.exists(keyStorePath)) {
            return JSON.createObjectNode();
        }
        try {
            String content = Files.readString(keyStorePath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return JSON.createObjectNode();
            }
            return (ObjectNode) JSON.readTree(content);
        } catch (IOException e) {
            throw new SecurityException("KEYSTORE_READ_FAILED", "读取密钥库失败: " + keyStorePath, e);
        }
    }

    private void writeStore(ObjectNode root) {
        try {
            if (keyStorePath.getParent() != null) {
                Files.createDirectories(keyStorePath.getParent());
            }
            Files.writeString(keyStorePath, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SecurityException("KEYSTORE_WRITE_FAILED", "写入密钥库失败: " + keyStorePath, e);
        }
    }

    private static char[] resolvePasswordFromEnv() {
        // 先环境变量、后系统属性，与 ConfigLoader.resolveEnv 的优先级保持一致
        String password = System.getenv(MASTER_PASSWORD_ENV);
        if (password == null || password.isBlank()) {
            password = System.getProperty(MASTER_PASSWORD_ENV);
        }
        if (password == null || password.isBlank()) {
            throw new SecurityException("MASTER_PASSWORD_MISSING",
                    "未设置环境变量或系统属性 " + MASTER_PASSWORD_ENV);
        }
        return password.toCharArray();
    }
}
