# AgentService 设计

**文档版本**：v1.2.0
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
| v1.1.0 | 2026-10-06 | 明确流式与非流式必须都进入工具调用循环（修复流式路径缺失循环的缺陷） | @ACANX |
| v1.2.0 | 2026-10-07 | 修正编号重复：`## 3. 实现（0.1.0）` 与 `## 3. 推理循环` 重号，改为 `## 4.`（含子节 `4.1` / `4.2`） | @ACANX |

---

## 1. 接口

```java
public interface AgentService {
    String createSession(SessionConfig config);
    void closeSession(String sessionId);
    CompletableFuture<AgentResponse> chat(String sessionId, String input);
    void streamChat(String sessionId, String input, AgentEventListener listener);
    List<ToolDescriptor> listTools();
    ToolResult invokeTool(String toolName, Map<String, Object> params);
    List<MemoryEntry> recall(String sessionId, String query, int limit);
    void shutdown();
}
```

## 2. 工厂

`AgentServiceFactory.local(AhaConfig)` 创建 `LocalAgentService`；预留 daemon 模式 `remote(String endpoint)`。

## 3. 推理循环

`AgentEngine` 推理循环（见设计蓝图第七部分）：

1. 触发 PreStep 事件
2. 加载会话历史
3. 构建 ChatRequest
4. 触发 Request 事件（WATERFALL，可修改）
5. 调用 LLM
6. 循环处理工具调用（ToolPreExecute / ToolPostExecute 事件）
7. 持久化消息
8. 触发 SessionFlush 事件
9. 返回 AgentResponse

## 4. 实现（0.1.0）

`AgentEngine` 已实现非流式与流式两套推理：

```java
AgentResponse run(String sessionId, SessionConfig config, String model,
                  String input, CancellationToken token);

void stream(String sessionId, SessionConfig config, String model,
            String input, AgentEventListener listener, CancellationToken token);
```

- 非流式：工具调用循环，上限 `MAX_TOOL_ITERATIONS = 8`；每轮将 assistant 与 tool 消息写库
- 流式：累积 `CONTENT_DELTA` / `REASONING_DELTA` / `TOOL_CALL_DELTA` / `USAGE`，
  结束后按 `index` 聚合工具调用并执行
- 上下文：`system prompt` + 最近 `Memory.MaxContextEntries` 条历史 + 当前输入

> **两条路径必须行为一致**：流式同样进入工具调用循环，
> 即「推理 → 执行工具 → 将 `tool` 消息回灌 → 再推理」，上限同样是 8 轮。
> 每轮需将 `assistant`（含 `tool_calls`）加入 `messages` 并写库，
> 否则下一轮看不到模型自己发起的调用，`tool` 消息也会失去所依附的 assistant 消息。
>
> 早期版本的流式路径只调一次 LLM 就返回，模型永远看不到工具结果，
> 表现为“只打印 `[tool result]`、不给回答”——属实现缺口，与设计不符。

### 4.1 事件映射

| LLM `StreamEvent` | Agent 事件 |
|---|---|
| `CONTENT_DELTA` | `ContentEvent` |
| `TOOL_CALL_DELTA` | （累积）→ `ToolCallEvent` |
| `USAGE` | `UsageEvent` |
| `DONE` | `DoneEvent` |
| `REASONING_DELTA` | 不对外透出（仅 debug 日志） |
| `ERROR` | 日志记录，不中断流程 |

### 4.2 服务门面

`LocalAgentService` 实现 `AgentService`，并提供可注入构造器（1.0 起公开）：

```java
public LocalAgentService(AhaConfig config, LlmClient llmClient,
                         ToolRegistry toolRegistry, Path dbPath);
```

`chat` 在虚拟线程池上异步执行（返回 `CompletableFuture`），`streamChat` 同步回调。
工具调用前统一经过 `PermissionPolicy` 校验（详见 [ToolSystemDesign.md](ToolSystemDesign.md)）。
