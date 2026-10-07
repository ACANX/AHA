# LLM 适配器设计

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

## 1. 适配器 SPI

```java
public interface LlmProviderAdapter {
    String providerId();
    String supportedIrVersion();
    String convertRequest(ChatRequest irRequest, ProviderConfig config);
    ChatResponse convertResponse(String rawResponse, ProviderConfig config);
    StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config);
    String buildUrl(ProviderConfig config);
    Map<String, String> buildHeaders(ProviderConfig config);
}
```

## 2. 内置适配器

| 适配器 | providerId | 协议 |
|---|---|---|
| `OpenAiAdapter` | `openai-compatible` | OpenAI Chat Completions |
| `AnthropicAdapter` | `anthropic` | Anthropic Messages |
| `GeminiAdapter` | `gemini` | Gemini generateContent |

## 3. 国产模型与中转站

- 国产模型（DeepSeek / 智谱 GLM / 通义千问）走 `OpenAiAdapter`，内置预设
- 中转站（sub2api / new-api）作为 `openai-compatible` provider 接入
- 宽松解析、缺失 `usage` 降级、容忍缺失 `[DONE]`、`tool_calls` 按 index 聚合、可配置鉴权

## 3.1 BaseUrl 语义（易错点）

`ProviderConfig.BaseUrl` 是**基础地址**，由适配器负责拼接端点路径，与 OpenAI / Anthropic 官方 SDK 的
`base_url` 习惯一致。用户只需填到版本前缀：

| 适配器 | `BaseUrl` 示例 | 实际请求 URL |
|---|---|---|
| `openai-compatible` | `https://api.deepseek.com/v1` | `.../v1/chat/completions` |
| `anthropic` | `https://api.anthropic.com` | `.../v1/messages` |
| `gemini` | `https://generativelanguage.googleapis.com` | `.../v1beta/models/{model}:generateContent` |

为兼容旧的“完整端点”写法，`OpenAiAdapter` 与 `AnthropicAdapter` 在地址已包含端点后缀时不再重复拼接。
若请求返回 404，先核对 `BaseUrl` 是否多写或少写了版本前缀。

## 4. 发现机制

`LlmAdapterRegistry` 通过 `ServiceLoader.load(LlmProviderAdapter.class)` 发现内置与第三方适配器。

## 5. DefaultLlmClient（实现）

`DefaultLlmClient` 承担与适配器正交的传输职责：

| 职责 | 实现 |
|---|---|
| 传输 | `java.net.http.HttpClient`（连接超时 10s，跟随重定向） |
| 超时 | 按 `ProviderConfig.TimeoutSeconds`（默认 120s） |
| 重试 | 5xx 指数退避，`min(8000, 2^n * 500) ms`，次数由 `MaxRetries` 控制 |
| 限流 | 按供应商 `RateLimit.Rpm` 的令牌桶（0 表示不限） |
| 流式 | SSE 行解析，容忍 `event:` / 注释行 / 无 `[DONE]` |
| 取消 | `CancellationToken` 在每行迭代前检查 |
| 并发 | 虚拟线程执行器（`newVirtualThreadPerTaskExecutor`） |
| 可靠析 | 实现 `AutoCloseable` |

选择供应商：优先取 `Model.yml` 的 `Default`（经合并后即 `LlmConfig.defaultProvider`），
可由 `ChatRequest.extensions` 的 `Provider` 键覆写；均不可用时回退 `Llm.Fallback.Provider`。

### 5.0 配置分离

供应商明细不再内联于主配置，而是独立的 `Model.yml`；主配置只声明兜底模型与文件路径。
实例化时由 `ConfigLoader` 合并（内置预设 → 内联 `Llm.Providers` → `Model.yml`），
`provider use` 可直接切换默认供应商。详见 [配置指南](../Guide/ConfigurationGuide.md)。

### 5.1 错误码

| 错误码 | 场景 |
|---|---|
| `NO_PROVIDER` | 未配置任何供应商 |
| `PROVIDER_NOT_FOUND` | 默认键名不存在 |
| `ADAPTER_NOT_FOUND` | 适配器 ID 无对应实现 |
| `LLM_HTTP_<status>` | 4xx/5xx（重试耗尽后） |
| `LLM_IO` | 传输层 I/O 失败 |
| `LLM_STREAM_IO` / `LLM_STREAM_INTERRUPTED` | 流式传输异常 |
