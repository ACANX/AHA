# 远程接入与协议演进设计

**文档版本**：v1.1.0
**状态**：冻结（规划基线）
**生效日期**：2026-10-06
**最后更新**：2026-10-06
**负责人**：@ACANX
**适用版本**：AHA 0.1.x 起（契约），1.0+ 实现
**关联文档**：`Docs/AHA/AHA-Design-V1.md`、`AgentServiceDesign.md`、`ToolSystemDesign.md`、`SecurityDesign.md`

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本；确立 1.0+ 远程/协议/调度能力的前向兼容契约 | @ACANX |
| v1.1.0 | 2026-10-07 | 关联文档路径补全为 `Docs/AHA/AHA-Design-V1.md` | @ACANX |

---

## 1. 目标能力（1.0 及以后）

| 编号 | 能力 | 目标版本 |
|---|---|---|
| G1 | 支持 ACP 等主流 Agent 连接协议，作为客户端与服务端双向接入 | 1.1 / 1.2 |
| G2 | 远程连接局域网 / 公网 Linux 服务器，在远程实例上执行 Agent 任务 | 1.0 / 1.1 |
| G3 | 通过定时任务或远程调用下发指定任务（cron / RPC / MQ） | 1.1 |
| G4 | 在云函数（Serverless）环境中调用 AHA 的 Agent 能力处理动态下发任务 | 1.2 |
| G5 | Win11 + WSL 场景：远程或直连方式调用 WSL 内的 Agent 能力 | 1.0 / 1.1 |

### 1.1 协议澄清（避免概念混用）

| 缩写 | 全称 | 定位 | 与 AHA 的关系 |
|---|---|---|---|
| **ACP** | Agent Client Protocol | 编辑器/IDE ↔ Agent 的会话协议（JSON-RPC，stdio/HTTP） | AHA 既作 **Server**（被 IDE 调用），也作 **Client**（调用其他 Agent） |
| **MCP** | Model Context Protocol | Agent ↔ 工具/数据源 | AHA 作 **Client**（消费外部工具），`McpAdapter` 已预留 |
| **A2A** | Agent2Agent | Agent ↔ Agent 协作（跨厂商） | AHA 作 **Server/Client** |
| OpenAI 兼容 | Chat Completions / Responses | LLM 推理 | 已实现（`OpenAiAdapter`） |

> 结论：**这些协议不应硬编码进内核**。AHA 只在契约层定义"任务接入点"，
> 协议差异由独立适配模块承担。

---

## 2. 分层设计

```
┌──────────────────────────────────────────────────────────────┐
│ 接入形态（1.0+ 新增，不修改内核）                              │
│  aha-cli   aha-desktop   aha-acp   aha-mcp   aha-server       │
│  aha-remote(ssh)   aha-wsl   aha-cloudfn   aha-scheduler      │
└───────────────┬──────────────────────────────────────────────┘
                │  AgentServiceProvider（SPI，ServiceLoader）
┌───────────────▼──────────────────────────────────────────────┐
│ 内核（0.1 已稳定）                                             │
│  AgentService ──> AgentEngine ──> LlmClient / ToolRegistry   │
│  AgentRuntime（运行时抽象）  TaskRequest / TaskResult（任务契约）│
│  RuntimeDescriptor / Endpoint（寻址契约）                      │
└──────────────────────────────────────────────────────────────┘
```

**核心不变量**：内核永不 `requires` 任何传输/协议模块；新增形态只是新增模块 + 一行 `provides`。

---

## 3. 0.1 已落地的前向兼容契约

> 以下能力在 0.1 已实现且有测试覆盖，是后续演进的稳定地基。

### 3.1 寻址契约（`aha-common`）

`com.acanx.module.aha.common.runtime`：

| 类型 | 作用 |
|---|---|
| `Endpoint` | 统一寻址语法，`scheme` 决定分派给哪个 Provider |
| `RuntimeKind` | `LOCAL` / `WSL` / `CONTAINER` / `SSH` / `SERVERLESS` / `DAEMON` |
| `RuntimeDescriptor` | 实例标识、OS、架构、工作目录、是否远程、附加属性 |

寻址示例：

```
local://
wsl://Ubuntu?user=me
ssh://deploy@build-01:22
daemon://127.0.0.1:8787
ws://relay.example.com/agent
https://cloud.example.com/fn/aha
```

### 3.2 任务契约（`aha-core`）

`com.acanx.module.aha.core.task`：

| 类型 | 作用 |
|---|---|
| `TaskRequest` | 跨进程 / 跨网络 / 跨云统一入参，PascalCase JSON，**字段缺失可解析** |
| `TaskResult` | 统一出参，含 `errorCode`、`toolCalls`、`durationMillis`、`metadata` |
| `TaskSource` | `INTERACTIVE` / `CLI` / `SCHEDULED` / `REMOTE_CALL` / `SERVERLESS` / `MCP` / `ACP` / `WEBHOOK` / `PLUGIN` |

`TaskRequest` 关键字段（已支持前向兼容）：

```json
{
  "Id": "task-1",
  "ParentId": null,
  "Source": "SCHEDULED",
  "SessionId": "session-1",
  "Input": "汇总今日错误日志",
  "SystemPrompt": "你是运维助手",
  "Model": "gpt-4o",
  "Context": { "Tenant": "acme" },
  "DeadlineEpochMillis": 0,
  "Extensions": { "EnableThinking": true }
}
```

> **兼容性设计**：`DeadlineEpochMillis`、`DurationMillis` 使用包装类型，
> 并声明 `@JsonIgnoreProperties(ignoreUnknown = true)`。
> 因此**旧版本可安全消费新版本载荷**（未知字段忽略、缺省字段不报错），
> 这是滚动升级与混合版本集群的前提。

### 3.3 统一提交入口（`AgentService`）

```java
default TaskResult submit(TaskRequest request, CancellationToken token);
```

- `SessionId` 为空时自动创建会话并在结束后关闭
- 异常统一转换为 `TaskResult.failure`，**不向上抛出**
- `metadata` 回填 `SessionId` 与 `Runtime`

调度器、协议适配器、云函数入口、MQ 消费者只需调用 `submit`，无需了解会话生命周期。

### 3.4 运行时抽象（`AgentRuntime`）

```java
public interface AgentRuntime extends AutoCloseable {
    RuntimeDescriptor descriptor();
    boolean isAvailable();
    TaskResult submit(TaskRequest request, CancellationToken token);
    default void submitStreaming(TaskRequest request, Consumer<AgentEvent> sink, CancellationToken token);
}
```

默认 `submitStreaming` 把同步结果包装为 `ContentEvent` + `DoneEvent` 序列，
远程实现只需覆写 `submit` 即可获得可用的流式语义。

### 3.5 服务发现 SPI（`AgentServiceProvider`）

```java
public interface AgentServiceProvider {
    String scheme();                                       // "wsl" / "ssh" / "daemon" / "acp"
    AgentService create(Endpoint endpoint, AhaConfig config);
    default boolean supports(Endpoint endpoint) { ... }
    default int priority() { return 0; }
}
```

`AgentServiceFactory`：

```java
AgentService local   = AgentServiceFactory.local(config);
AgentService remote  = AgentServiceFactory.connect("ssh://deploy@build-01:22", config);
Set<String>  schemes = AgentServiceFactory.supportedSchemes();   // 含 "local"
```

当无匹配 Provider 时抛出结构化错误（便于上层降级或给出安装提示）：

```
AhaException[NO_PROVIDER_FOR_ENDPOINT]:
  尚无支持 ssh:// 的运行时实现（计划于 1.0 及后续版本提供）: ssh://deploy@build-01:22
```

> 0.1 该路径已有测试覆盖：`AgentServiceFactoryTest`。

---

## 4. 演进路线

### 4.1 版本规划

| 版本 | 主题 | 交付 |
|---|---|---|
| **1.0** | API 冻结 | 契约冻结（`AgentService` / `TaskRequest` / `AgentRuntime` / `Endpoint`）；扩展生态 |
| **1.1** | 远程与调度 | `aha-remote`（SSH 运行 Linux 实例）、`aha-wsl`、`aha-scheduler`（cron）、`submit` 远程化 |
| **1.2** | 协议接入 | `aha-acp`（Server + Client）、`aha-mcp`（工具消费，落地 `McpAdapter`）、HTTP/WebSocket 传输 |
| **1.3** | 云原生 | `aha-cloudfn`（AWS Lambda / 阿里云 FC / 腾讯云 SCF）、容器镜像、K8s Job 模板 |
| **2.0** | 分布式运行时 | 任务编排（DAG）、多实例调度、状态与结果持久化、可观测性（OpenTelemetry） |

### 4.2 各能力实现方案

#### G2 远程 Linux（1.0 / 1.1）

新增模块 `aha-remote`：

```java
public final class SshAgentServiceProvider implements AgentServiceProvider {
    @Override public String scheme() { return "ssh"; }
    @Override public AgentService create(Endpoint endpoint, AhaConfig config) {
        return new RemoteAgentService(SshTransport.connect(endpoint, config));
    }
}
```

- 传输：SSH（`sshj` 或复用系统 `ssh` 子进程），首版可用"远程执行 `aha run --json`"的**最小可用形态**
- 认证：密钥优先，禁止内嵌明文口令；凭据走 `SecretStore`
- 数据面：远端只回传 `TaskResult`/事件流，上下文不落远端（可配置）
- 前置条件：远端需有 AHA 运行时（JAR + JDK 25），或复用本地 `Pipeline` 模式

#### G5 WSL（1.0 / 1.1）

WSL 与 SSH 的最大差异是**无需网络与认证**，可直连：

```java
public final class WslAgentServiceProvider implements AgentServiceProvider {
    @Override public String scheme() { return "wsl"; }
}
```

- 探测：读取 `HKCU\Software\Microsoft\Windows\CurrentVersion\Lxss` 或调用 `wsl -l -q`
- 调用：`wsl [-d <distro>] -u <user> --cd <dir> -- <cmd>`（进程桥接），或经 WSL 内的 daemon
- 路径映射：Windows ↔ WSL 路径双向转换（`C:\a` ↔ `/mnt/c/a`），在 `Endpoint.params` 中声明工作目录
- 权限：Windows 侧需显式开关（`Tools.AutoApprove` 不适用于跨边界调用）

#### G3 定时与远程下发（1.1）

新增 `aha-scheduler`（可选独立进程）：

- 任务定义：`TaskDefinition`（cron 表达式 + `TaskRequest` 模板 + 目标 `Endpoint`）
- 触发：内置 cron 解析，或对接系统 cron / K8s CronJob
- 下发：`AgentServiceFactory.connect(endpoint).submit(request, token)`
- 幂等：`TaskRequest.Id` + `ParentId`；`metadata` 记录 attempt
- 重试：指数退避 + 死信队列（可插拔 `TaskQueue`）

#### G4 云函数（1.2）

新增 `aha-cloudfn`，提供**薄适配入口**：

```java
// 伪代码：阿里云 FC
public class AhaHandler implements PojoRequestHandler<TaskRequest, TaskResult> {
    private final AgentService service = AgentServiceFactory.local(ConfigLoader.loadDefault());
    @Override public TaskResult handleRequest(TaskRequest request, Context context) {
        return service.submit(request, new CancellationToken());
    }
}
```

要点：

- **启动开销**：冷启动需避免加载全部扩展；提供 `aha-core-lite` 或延迟初始化
- **无状态**：`Memory.Storage` 切为内存或外部存储（1.3 引入 `Memory.Storage: remote`）
- **超时**：尊重 `TaskRequest.DeadlineEpochMillis`，与平台超时对齐
- **回调**：长任务改为"提交 + 回调"，`TaskRequest.Context` 携带回调地址

#### G1 协议接入（1.2）

新增 `aha-acp`、`aha-mcp`，二者都实现 `AgentServiceProvider` 或直接复用 `submit`：

- `aha-acp`：JSON-RPC 能力协商（`initialize` / `session/*` / `prompt`），stdio 与 HTTP 两种传输
- `aha-mcp`：作为 Client 消费外部 MCP 服务器提供的工具，落地 `McpAdapter`，并注册进 `ToolRegistry`
- 双向：ACP 的 Server 端把 `prompt` 请求转为 `TaskRequest`；Client 端把远端 Agent 视作 `AgentServiceProvider`

> 协议层**不进入内核**，`aha-core` 不 `requires` 任何协议库。

---

## 5. 配置 schema 预留（1.0 生效）

0.1 不实现，但 schema 已规划（写入 `Aha.yaml` 时 0.1 会因未映射字段而被忽略，
因为 `AhaConfig` 标注了 `@JsonIgnoreProperties(ignoreUnknown = true)`）：

```yaml
Aha:
  Remote:
    Enabled: false
    DefaultEndpoint: "ssh://deploy@build-01:22"
    Endpoints:
      Build01:
        Endpoint: "ssh://deploy@build-01:22"
        Auth: "key:/home/me/.ssh/id_ed25519"
        WorkDir: /srv/aha
        TimeoutSeconds: 300
      WslUbuntu:
        Endpoint: "wsl://Ubuntu?user=me"
        WorkDir: /home/me/workspace
    Server:
      Enabled: false
      Bind: "127.0.0.1:8787"
      Protocol: daemon
      Auth: "token:${AHA_SERVER_TOKEN}"

  Scheduler:
    Enabled: false
    Tasks:
      DailyDigest:
        Cron: "0 9 * * *"
        Endpoint: "local://"
        Input: "汇总昨日错误日志并生成摘要"
        Source: SCHEDULED
        Retry: 2

  Protocols:
    Acp:
      Enabled: false
      Role: server          # server | client
      Transport: stdio      # stdio | http
    Mcp:
      Enabled: false
      Servers:
        FsServer:
          Url: "stdio:node /opt/mcp-fs.js"
```

**配置迁移承诺**：以上字段一旦在 1.0 冻结，后续仅做**新增**，不重命名、不移除。

---

## 6. 安全模型（跨边界前提）

远程形态引入新的攻击面，1.0 前必须完成：

| 项 | 要求 |
|---|---|
| 认证 | SSH 密钥 / Token；禁止明文口令；`SecretStore` 统一托管 |
| 授权 | 跨边界任务默认 `deny`；`PermissionPolicy` 对远程来源更严格（`TaskSource` 参与判定） |
| 传输加密 | SSH / TLS；内网可配置豁免但需显式声明 |
| 审计 | 每次远程任务记录 `TaskSource`、`Endpoint`、`RuntimeDescriptor`、发起者 |
| 隔离 | 远程执行的工具白名单独立于本地（`Tools.Enabled` 按 Endpoint 覆盖） |
| 会话 | 远程会话 ID 不跨实例复用；避免上下文串扰 |
| 反序列化 | `TaskRequest` 走白名单反序列化，禁止任意类型（`@JsonTypeInfo` 慎重使用） |

---

## 7. 测试策略

| 层级 | 方式 |
|---|---|
| 契约兼容性 | 0.1 已覆盖：`EndpointTest`、`RuntimeDescriptorTest`、`TaskModelTest`（含"旧版本消费新载荷"） |
| SPI 发现 | 0.1 已覆盖：`AgentServiceFactoryTest`（含 `NO_PROVIDER_FOR_ENDPOINT`） |
| 传输层 | 1.1+：Testcontainers 起 Linux 容器模拟 SSH 目标；Mock SSH 服务器 |
| WSL | 1.1+：Windows CI 上跑真实 WSL；非 Windows 用进程桥接 mock |
| 云函数 | 1.2+：各平台 Runtime Interface Emulator / 本地 invoke |
| 协议 | 1.2+：ACP/MCP 官方规范的一致性用例 + 交互式契约测试 |
| 兼容性回归 | 每次发布跑"上一个 minor 版本载荷 → 当前版本解析"的矩阵 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 协议标准演进快（ACP/MCP/A2A 均未定稿） | 过早抽象导致返工 | 0.1 **不做协议抽象**，只做 `AgentServiceProvider`；协议模块独立演进 |
| 远程执行引入 RCE 风险 | 安全事故 | 默认 deny + 白名单 + 审计 + 密钥鉴权（见第 6 节） |
| 云函数冷启动过慢 | 无法满足平台超时 | 提供 lite 产物、延迟初始化、预热实例 |
| 混合版本集群字段不兼容 | 解析失败 | `TaskRequest`/`TaskResult` 使用包装类型 + 忽略未知字段（0.1 已落地） |
| WSL 路径与权限差异 | 任务失败 | 路径映射工具 + 明确 `WorkDir` + 前置探测（`wsl -l -q`） |
| 远端 JDK 版本不一致 | 无法运行 | 远端要求 JDK 25 LTS；提供 `aha doctor --remote` 预检 |
| 契约冻结后仍频繁变动 | 生态受损 | 1.0 冻结；变更走版本号 + 迁移说明（见 `InternalProtocolSpec.md` 同一套规则） |

---

## 9. 0.1 阶段的"准备完成度"自查

| 准备项 | 状态 |
|---|---|
| 寻址契约（`Endpoint`） | ✅ 已实现 + 测试 |
| 运行时契约（`RuntimeKind` / `RuntimeDescriptor` / `AgentRuntime`） | ✅ 已实现 + 测试 |
| 任务契约（`TaskRequest` / `TaskResult` / `TaskSource`） | ✅ 已实现 + 测试（含跨版本兼容） |
| 统一提交入口（`AgentService.submit`） | ✅ 已实现 + 测试 |
| 服务发现 SPI（`AgentServiceProvider`） | ✅ 已实现 + 测试 |
| 内核零传输依赖 | ✅ 保持不变 |
| 远程配置 schema 规划 | ✅ 本文档第 5 节 |
| 安全模型规划 | ✅ 本文档第 6 节 |
| 协议模块独立演进策略 | ✅ 本文档第 8 节 |

### 9.1 后续版本开工清单

1.0 之后实现任一形态时，**只需**：

1. 新建模块（如 `aha-remote`），`requires aha-core`
2. 实现 `AgentServiceProvider`（必要时实现 `AgentRuntime`）
3. `module-info.java` 声明 `provides`，并补 `META-INF/services`
4. 加入 `Aha.Remote.Endpoints` 配置段
5. 上层无需改动 —— `AgentServiceFactory.connect(...)` 自动可用

> 若发现需要修改 `aha-core` 才能接入，则说明契约设计有缺陷，应先修契约再实现形态。
