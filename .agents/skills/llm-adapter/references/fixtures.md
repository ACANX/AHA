# 适配器 Fixture

## 真实 fixture 位置

适配器的固定测试数据统一放在：

```
aha-core/src/test/resources/Fixtures/
├── OpenAiChatResponse.json
├── OpenAiStreamEvents.json
├── AnthropicChatResponse.json
├── AnthropicStreamEvents.json
├── GeminiChatResponse.json
└── GeminiStreamEvents.json
```

本技能目录内**不再存放 fixture 副本**，避免出现两份会各自漂移的真相。

## 为什么用固定 fixture

适配器测试不做真实网络调用：上游响应格式、字段命名、流式分片方式都是外部契约，
必须用冻结的样本锁定。这样测试可离线运行、可重复、不受供应商限流影响。

## 新增 fixture 的约定

- 命名：`<Provider><场景>.json`，如 `MistralStreamEvents.json`
- 内容：取自供应商文档或一次真实响应的**脱敏**副本
- 流式样本需覆盖边界：分片截断、多字节字符跨片、空事件、终止标记
- 不要放入真实 API Key、账号信息或用户隐私数据

## 相关测试

参考 `DefaultLlmClientTest`：它用自包含的 `ServerSocket` 实现替代 `com.sun.net.httpserver`
（该 JDK 模块在 module-path 上对测试不可见），从而在不引入额外依赖的前提下验证
SSE 解析、重试与取消。
