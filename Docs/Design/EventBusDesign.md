# 事件总线设计

**文档版本**：v1.1.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本 | @ACANX |
| v1.1.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、包名、类名、配置段与描述符 | @ACANX |

---

## 1. 接口

```java
public interface EventBus {
    <E extends ExtensionEvent> void emit(EventKey<E> key, E event);
    <E extends ExtensionEvent> void on(EventKey<E> key, ExtensionEventListener<E> listener);
    <E extends ExtensionEvent> E waterfall(EventKey<E> key, E event);
}
```

## 2. 分发模式

`EMIT` / `WATERFALL` / `SERIAL` / `PARALLEL`

**Waterfall 语义**：监听器接收 `(event, next)`，可变换、短路、包装。不调用 `next()` 直接返回则短路。仅做观察的监听器必须委托。

## 3. 预定义事件

| 事件键 | 类型 | 分发模式 |
|---|---|---|
| `agent/pre-step` | `PreStepEvent` | WATERFALL |
| `agent/request` | `RequestEvent` | WATERFALL |
| `tools/pre-execute` | `ToolPreExecuteEvent` | WATERFALL |
| `tools/post-execute` | `ToolPostExecuteEvent` | WATERFALL |
| `llm/stream` | `LlmStreamEvent` | WATERFALL |
| `system-prompt/assemble` | `SystemPromptEvent` | WATERFALL |
| `session/flush` | `SessionFlushEvent` | PARALLEL |
| `lifecycle` | `LifecycleEvent` | EMIT |

## 4. 实现

`EventBusImpl`（`com.acanx.module.aha.core.extension.event`），预定义键见 `AgentEvents`。

## 5. 实现现状

| 项 | 状态 |
|---|---|
| `EventBus` / `EventKey` / `DispatchMode` / `ExtensionEvent` | ✅ 接口已冻结（`aha-extension-api`） |
| 预定义事件类型（`RequestEvent`、`PreStepEvent`、`ToolPreExecuteEvent` 等） | ✅ 已定义（`aha-core`） |
| `EventBusImpl` 的 EMIT / SERIAL / PARALLEL | ⛔ 0.4 |
| `WATERFALL`（可变换、短路）语义 | ⛔ 0.4，目前仅为接口约定 |

**0.1 现状**：`AgentEngine` 直接调用 `AgentEventListener` 分发 Agent 级事件
（`ContentEvent` / `ToolCallEvent` / `ToolResultEvent` / `UsageEvent` / `DoneEvent`），
尚未接入扩展事件总线。接入后需保证两类事件语义一致。
