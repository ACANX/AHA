# 编码规范

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

## 1. 命名

| 元素 | 规范 | 示例 |
|---|---|---|
| 类名 | 大驼峰 | `AgentEngine` |
| 方法名 | 小驼峰 | `createSession` |
| 常量 | 全大写下划线 | `MAX_RETRY_COUNT` |
| 包名 | 全小写 | `com.acanx.module.aha.core` |
| 记录类 | 大驼峰 | `ChatRequest` |
| 枚举值 | 全大写 | `CONTENT_DELTA` |
| YAML 字段 | 大驼峰 | `DefaultProvider` |

## 2. 异常处理

- 所有异常必须被处理或包装为 `AhaException`
- 禁止空 catch 块
- 禁止在 Core 中调用 `System.exit()`
- 异常必须携带 `code` 字段

## 3. 日志

- 使用 SLF4J API
- 禁止使用 `System.out` / `System.err`（CLI 适配层除外）
- 敏感信息（API Key、密码）不得记录
- 日志级别：ERROR / WARN / INFO / DEBUG / TRACE

## 4. 并发

- 使用虚拟线程处理 I/O 密集型任务
- 禁止池化虚拟线程
- 使用 `CancellationToken` 传播取消
- 禁止使用 `Thread.stop()` / `Thread.suspend()`

## 5. Javadoc

所有公开 API 必须有 Javadoc（`@since`、`@param`、`@return`）。
