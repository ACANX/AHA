# 内部统一协议规范

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

## 1. 原则

IR 是 AHA 自定义的独立标准，不是任何外部协议的从属物。字段名、类型名、枚举值由 AHA 定义，拥有独立版本号。

## 2. 核心类型

- `Role`：`SYSTEM` / `USER` / `ASSISTANT` / `TOOL`
- `ChatRequest`：`model`、`messages`、`tools`、`temperature`、`maxTokens`、`stream`、`extensions`
- `ChatMessage`：`role`、`content`、`toolCalls`、`toolCallId`、`reasoningContent`
- `ToolCall` / `ToolCallDelta` / `ToolDefinition`
- `ChatResponse` / `Choice` / `Usage`
- `StreamEvent` / `StreamEventType`：`CONTENT_DELTA`、`REASONING_DELTA`、`TOOL_CALL_DELTA`、`USAGE`、`DONE`、`ERROR`
- `IrVersion.CURRENT = "1.0"`

## 3. 版本管理

| 变更类型 | 版本号变更 | 迁移要求 |
|---|---|---|
| 新增可选字段 | 次版本号 +1 | 现有适配器无需修改 |
| 新增枚举值 | 次版本号 +1 | 适配器需处理新值 |
| 修改字段类型 | 主版本号 +1 | 所有适配器必须修改 |
| 删除字段 | 主版本号 +1 | 所有适配器必须修改 |

## 3. 当前 IR（0.1.0）

| 类型 | 字段 |
|---|---|
| `ChatMessage` | `role`、`content`、`toolCalls`、`toolCallId`、`reasoningContent` |
| `ChatRequest` | `model`、`messages`、`tools`、`temperature`、`maxTokens`、`stream`、`extensions` |
| `ChatResponse` | `id`、`model`、`choices`、`usage`、`extensions` |
| `Choice` | `index`、`message`、`finishReason` |
| `Usage` | `promptTokens`、`completionTokens`、`totalTokens`、`reasoningTokens` |
| `ToolDefinition` | `name`、`description`、`parameters` |
| `ToolCall` | `id`、`name`、`arguments` |
| `ToolCallDelta` | `index`、`id`、`name`、`argumentsDelta` |
| `StreamEvent` | `type`、`contentDelta`、`reasoningDelta`、`toolCallDelta`、`usage`、`finishReason`、`errorCode`、`errorMessage` |

`StreamEventType`：`CONTENT_DELTA`、`REASONING_DELTA`、`TOOL_CALL_DELTA`、`USAGE`、`DONE`、`ERROR`。

IR 类型位于 `com.acanx.module.aha.core.llm.protocol`，不依赖任何供应商协议；
适配器负责 IR ↔ 外部协议的显式转换（非直接序列化）。
