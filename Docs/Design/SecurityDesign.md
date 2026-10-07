# 安全设计

**文档版本**：v1.0.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |

---

## 1. SecretStore

```java
public interface SecretStore {
    void store(String key, char[] secret);
    char[] retrieve(String key);
    void delete(String key);
}
```

## 2. EncryptedFileSecretStore

- 算法：AES-256-GCM
- 密钥派生：PBKDF2WithHmacSHA256
- 文件格式：JSON，字段加密后 Base64 编码
- 路径：`%APPDATA%\AHA\Key\Aha.keystore` 或 `~/.config/aha/Key/Aha.keystore`

## 3. 环境变量优先

`ApiKey: "${AHA_API_KEY_OPENAI}"`：环境变量存在则优先使用，否则从加密密钥库读取。

## 4. 预留

预留系统密钥链适配器（0.7 版本：Windows Credential Manager / macOS Keychain / libsecret）。

## 5. EncryptedFileSecretStore（实现）

| 项 | 实现 |
|---|---|
| 路径 | `Security.KeyStorePath`，默认 `$AHA_HOME/Key/Aha.keystore` |
| 加密 | AES-256-GCM |
| 派生 | PBKDF2WithHmacSHA256（随机 salt） |
| 条目格式 | `base64(salt):base64(iv):base64(ciphertext)` |
| 主密码 | 构造参数，缺省读环境变量 `AHA_MASTER_PASSWORD` |

未设主密码时，密钥库不可用，依配置回退到环境变量占位（`${AHA_XXX}`）。

### 5.1 密钥解析优先级

```
环境变量 > 加密密钥库 > 空值（使用时报 LLM_HTTP_401）
```

### 5.2 安全约定

- 密文与 salt/iv 分列存储，同一明文多次加密不产生相同密文
- `ApiKey` 不得写入日志；`provider test` 仅输出「已配置 / 未配置」
