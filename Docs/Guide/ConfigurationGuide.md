# 配置指南

**文档版本**：v1.16.0
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
| v1.1.0 | 2026-10-06 | 模型供应商配置分离至 `Model.yml`；补充一键切换与配置项参考 | @ACANX |
| v1.2.0 | 2026-10-06 | `Model.yml` 默认位置改为用户级目录（`~/.aha`），不再依赖工作目录 | @ACANX |
| v1.3.0 | 2026-10-06 | 新增 `Aha.Agent` 段与 Agent 身份/系统提示词说明 | @ACANX |
| v1.4.0 | 2026-10-06 | 新增「11. 日志」：说明 `Aha.Logging` 已程序化生效及默认记录的事件 | @ACANX |
| v1.5.0 | 2026-10-06 | 补充工具权限的交互式确认（`y`/`n`/`a`）与 `--yes` 用法 | @ACANX |
| v1.6.0 | 2026-10-06 | 默认路径 fallback 改为 `~/.aha`；`keys`→`key`；日志格式含类名/方法/行号 | @ACANX |
| v1.7.0 | 2026-10-06 | 补充 `Tools.Enabled` 与 Shell 白名单语义、密钥库凭据解析优先级 | @ACANX |
| v1.8.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、包名、类名、配置段与描述符 | @ACANX |
| v1.9.0 | 2026-10-07 | `Agent.SystemPrompt` 说明改为「默认非空、兜底生效」 | @ACANX |
| v1.10.0 | 2026-10-07 | `Agent.PromptFiles` 说明补「决定优先级与 `/memory` 默认文件名」 | @ACANX |
| v1.11.0 | 2026-10-07 | `Agent.PromptFiles` 补「同一层级内全部生效」 | @ACANX |
| v1.12.0 | 2026-10-07 | `PromptFiles` 默认值补 `CLAUDE.md` | @ACANX |
| v1.13.0 | 2026-10-07 | 身份注入口径统一为「每个来源各自作为一条独立的 system 消息，不拼接」 | @ACANX |
| v1.14.0 | 2026-10-07 | §11 日志：Console 固定级别更正为 `ERROR`；补切分规则（`<基名>-yyyy-MM-dd-NN.log`、补零序号、不压缩、不自动清理）；指向 LoggingDesign.md | @ACANX |
| v1.15.0 | 2026-10-07 | `Logging.Level` 默认值更正为 `DEBUG`（与 `AhaDefault.yaml` 一致），并说明开发阶段有意保留全量；区分「内置默认值」与「非法值兜底」 | @ACANX |
| v1.16.0 | 2026-10-08 | 第 11 节：日志装配说明改为「入口（CLI / 桌面端）」，并指明 `LoggingSetup` 在 `aha-core` | @ACANX |

---

## 1. 两份配置文件

AHA 的配置分为两份文件，职责分离：

| 文件 | 职责 | 加载位置 |
|---|---|---|
| `Aha.yaml` | 运行参数 + 兜底（降级回退）模型 | 项目 `./Aha.yaml`，缺省 classpath `AhaDefault.yaml` |
| `Model.yml` | 模型供应商明细 + 当前默认供应商 | **用户级目录**：`$AHA_HOME/Model.yml`，未设 `AHA_HOME` 时为 `~/.aha/Model.yml`；缺省 classpath `ModelDefault.yml` |

**默认行为**：新增模型配置（`aha provider add`）与切换默认供应商（`aha provider use`）都写入 `Model.yml`，不修改主配置。

### 1.1 初始化

首次使用执行一次即可生成配置文件与目录结构，无需手工编写 YAML：

```bash
aha init                                   # 交互式：选供应商 + 可选写入 API Key
aha init --provider DeepSeek               # 指定默认供应商
aha init --provider DeepSeek --api-key sk-xxx
aha init --no-input                        # 非交互，只生成内置全量配置
```

详见 [GettingStarted.md](GettingStarted.md) 第 3 节。

### 1.2 为何 `Model.yml` 放用户级目录

模型配置包含 API Key，属于**凭据类配置**，因此采用业界通行做法放在用户主目录而非工作目录：

| 工具 | 凭据位置 |
|---|---|
| AWS CLI | `~/.aws/credentials` |
| Docker | `~/.docker/config.json` |
| npm | `~/.npmrc` |
| **AHA** | **`~/.aha/Model.yml`** |

不读取工作目录下的 `./Model.yml`，因为相对路径会随 CWD 变化，且读不到时会静默回退内置默认，难以排查。

**确需项目级配置时，在主配置中显式指定**：

```yaml
Aha:
  Llm:
    ModelFile: "./Model.yml"      # 显式项目级；该文件含密钥，必须加入 .gitignore
```

> 注意：`./` / `../` 与 `~/` 开头的路径均受支持，`~` 会展开为用户主目录。

## 2. 主配置 `Aha.yaml`

### 2.1 Agent 身份与系统提示词

AHA 会在会话创建时解析 Agent 的身份设定。每个身份来源**各自作为一条独立的 `system` 消息**注入，按「用户级 → 项目级 → 运行环境块」顺序排在会话历史之前，不拼接成一整段。优先从约定文件读取：

```yaml
Aha:
  Agent:
    # 按顺序查找，取第一个非空文件
    PromptFiles:
      - AHA.md
      - AGENTS.md
    # 仅当未找到任何文件时使用
    SystemPrompt: ""
```

**文件查找顺序**（从当前工作目录逐级向上，就近优先）：

```
<工作目录>/AHA.md  →  <工作目录>/AGENTS.md
   ↑ 逐级向上
<项目根>/AHA.md    →  <项目根>/AGENTS.md
   ↓ 回退
$AHA_HOME/AHA.md  或  ~/.aha/AHA.md      个人全局身份
   ↓ 仍无
Aha.Agent.SystemPrompt（内联）
   ↓ 仍无
内置默认身份
```

直接编辑文件即可定义 Agent 角色：

```markdown
<!-- AHA.md -->
你是「代码审查员」，只做代码审查，不写新功能。

输出要求：
- 先给结论（通过 / 不通过）
- 再列问题，每条标注严重级别
```

启动时会显示实际来源，便于确认：

```
  身份来源     /path/to/project/AHA.md（63 字符）
```

可用 `--system` / `--system-file` 临时覆盖：

```bash
aha chat --system "你是一个只会说喵的猫"
aha chat --system-file ./memorys/reviewer.md
```

> **关于“不重复注入”**：身份设定在**会话创建时**解析并固化到会话记录，
> 同一会话内的后续轮次不会重复读取文件或重复解析。
> 但 LLM API 是无状态的，**每一轮请求仍需携带 system 消息**，否则模型会失去身份设定。
> “不重复”指的是不重复配置，而非不发送。
>
> 复用会话（`--session <id>`）时沿用该会话已固化的身份，不会重新解析文件。

### 2.2 模型相关

模型相关部分只保留兜底模型与模型文件路径：

```yaml
Aha:
  Llm:
    # 兜底（降级回退）模型：Model.yml 缺失、默认供应商不存在或调用失败时使用
    Fallback:
      Provider: OpenAI
      Model: gpt-4o
    # 模型供应商配置文件路径（支持 ${ENV} / ${ENV:-default} 占位，以及路径开头的 ~ 展开）
    ModelFile: "${AHA_HOME:-~/.aha}/Model.yml"

  Memory:
    Storage: sqlite
    Path: "${AHA_HOME:-~/.aha}/Data/Aha.db"
    MaxContextEntries: 100
```

其余段（`Memory`、`Tools`、`Security`、`Logging`、`Extension`）保持不变，完整默认值见 `aha-core/src/main/resources/AhaDefault.yaml`。

## 3. 模型配置 `Model.yml`

默认位置：`$AHA_HOME/Model.yml`（未设 `AHA_HOME` 时为 `~/.aha/Model.yml`）。
文件不存在时使用 classpath 内置的 `ModelDefault.yml`，首次 `provider add/use/remove` 会创建它。

查看当前实际生效的路径：

```bash
aha config get Llm.ModelFile
```

```yaml
Model:
  Default: DeepSeek          # 当前默认（选中）供应商

  Providers:
    DeepSeek:
      Adapter: openai-compatible
      BaseUrl: https://api.deepseek.com/v1
      ApiKey: "${AHA_API_KEY_DEEPSEEK}"
      Model: deepseek-chat
      TimeoutSeconds: 120
      MaxRetries: 3
      RateLimit:
        Rpm: 60
        Tpm: 100000
```

内置默认（`ModelDefault.yml`）提供 6 个供应商：
`OpenAI`、`Anthropic`、`Gemini`、`DeepSeek`、`BigModelCN`、`Qwen`。

## 4. 一键切换供应商

```bash
aha provider list                          # 列出全部供应商，标记当前默认
aha provider use DeepSeek                  # 一键切换默认供应商
aha provider test DeepSeek                 # 查看配置与所需环境变量（不发起请求）
```

切换后 `Model.yml` 的 `Default` 字段被更新：

```yaml
Model:
  Default: DeepSeek
```

## 5. 新增 / 删除供应商

```bash
# 新增（自动生成 ${AHA_API_KEY_<ID>} 占位符）
aha provider add MyProxy \
    --adapter openai-compatible \
    --base-url https://proxy.example/v1 \
    --model gpt-4o-mini \
    --default

# 自定义 API Key 占位符
aha provider add MyProxy --adapter openai-compatible \
    --base-url https://proxy.example/v1 --model gpt-4o-mini \
    --api-key '${MY_CUSTOM_KEY}'

# 删除（需显式确认）
aha provider remove MyProxy -y
```

供应商 ID 必须为 **PascalCase**（如 `DeepSeek`、`MyProxy`），与 YAML 字段命名规范一致。

新增参数：

| 选项 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `--adapter` | ✅ | — | `openai-compatible` / `anthropic` / `gemini` |
| `--base-url` | ✅ | — | 基础地址（如 `https://api.deepseek.com/v1`），适配器自动拼接端点路径 |
| `--model` | ✅ | — | 模型名 |
| `--api-key` | — | `${AHA_API_KEY_<ID>}` | 支持 `${ENV}` 占位 |
| `--timeout` | — | 120 | 超时秒数 |
| `--max-retries` | — | 3 | 5xx 重试次数 |
| `--rpm` | — | 0 | 每分钟请求上限，0 表示不限 |
| `--default` | — | false | 同时设为默认供应商 |

## 6. 合并优先级

```
内置预设（classpath:ModelDefault.yml）
    ↓ 覆盖
主配置内联 Llm.Providers（兼容用，可选）
    ↓ 覆盖
Model.yml
    ↓ 覆盖
环境变量（${ENV} / ${ENV:-default}）
```

默认供应商选取顺序：`Model.yml.Default` → 主配置 `Llm.DefaultProvider` → `OpenAI`。

## 7. 环境变量解析

| 语法 | 行为 |
|---|---|
| `${AHA_API_KEY_OPENAI}` | 读环境变量，无值时读同名系统属性；仍未设置则**保留原文**（便于提示所需变量） |
| `${AHA_HOME:-~/.aha}` | 无值时使用 `~/.aha`（用户级目录） |

> 保留未解析占位符是刻意设计：`aha provider test` 会据此提示所需环境变量名，
> 而不是静默变成空字符串。

## 8. 校验

`ConfigLoader` 递归校验字段名，非 PascalCase 字段会抛出 `ConfigException`（`INVALID_FIELD_NAME`）。

两份文件均受此约束；`Model.yml` 的顶层 `Model`、`Default`、`Providers` 及各供应商 ID 都需为 PascalCase。

## 9. 配置项参考（0.1.0）

### 9.1 主配置 `Aha.yaml`

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `Agent.PromptFiles` | list | 身份文件候选名，按顺序决定优先级；**同一层级内全部候选都生效**，层级顺序为「用户级 → 项目级」。同时决定 `/memory` 的默认写入文件名；新增兼容文件（如 `CLAUDE.md`）只需在此追加 | 身份文件候选名，按顺序决定优先级。**同时决定 `/memory` 的默认写入文件名**；新增兼容文件（如 `CLAUDE.md`）只需在此追加 | `[AHA.md, AGENTS.md]` | 提示词文件名，逐级向上查找取首个非空文件 |
| `Agent.SystemPrompt` | string | 兜底系统提示词。默认非空（AHA 自带身份声明）；只有一份身份文件都没找到时才用它。`/config` 可见 | 内置 AHA 身份声明（约 700 字符，见 `AhaDefault.yaml`） | 内联系统提示词；仅当未找到任何文件时生效 |
| `Llm.Fallback.Provider` | string | `OpenAI` | 兜底供应商键名 |
| `Llm.Fallback.Model` | string | `gpt-4o` | 兜底模型名，覆盖供应商自身模型 |
| `Llm.ModelFile` | string | `${AHA_HOME:-~/.aha}/Model.yml` | 模型配置文件路径（支持 `${ENV}` 占位与 `~` 展开）|
| `Llm.DefaultProvider` | string | — | 内联兼容用；通常改由 `Model.yml.Default` 提供 |
| `Llm.Providers` | map | — | 内联兼容用；优先于内置预设，低于 `Model.yml` |
| `Memory.Storage` | string | `sqlite` | 存储后端 |
| `Memory.Path` | string | `${AHA_HOME:-~/.aha}/Data/Aha.db` | SQLite 路径 |
| `Memory.MaxContextEntries` | int | 100 | 上下文窗口条目上限 |
| `Tools.Enabled` | list | 5 个内置工具 | 启用的工具名；留空表示不过滤 |
| `Tools.AutoApprove` | list | `[]` | 额外自动放行的权限（`WRITE`/`EXECUTE`/`ADMIN`/`ALL`） |
| `Tools.Shell.TimeoutSeconds` | int | 30 | Shell 工具超时 |
| `Security.KeyStore` | string | `encrypted-file` | 密钥库实现 |
| `Security.KeyStorePath` | string | `${AHA_HOME:-~/.aha}/Key/Aha.keystore` | 密钥库路径 |
| `Logging.Level` | string | `DEBUG` | 日志级别（`TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR`/`FATAL`/`OFF`）；内置默认 `DEBUG`，开发阶段有意保留全量 |
| `Logging.File` | string | `${AHA_HOME:-~/.aha}/Log/AHA.log` | 日志文件；超过 10 MB 切分，切分文件名为 `<基名>-yyyy-MM-dd-NN.log` |
| `Extension.Enabled` | boolean | `true` | 扩展总开关 |
| `Extension.Path` | string | `${AHA_HOME:-~/.aha}/Extension` | 扩展目录 |
| `Extension.AutoLoad` | boolean | `true` | 启动时自动加载 |
| `Extension.Disabled` | list | `[]` | 禁用的扩展 ID |
| `Extension.Settings` | map | `{}` | 各扩展设置 |

### 9.2 模型配置 `Model.yml`

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `Model.Default` | string | `OpenAI` | 当前默认供应商键名 |
| `Model.Providers.<Id>.Adapter` | string | — | `openai-compatible` / `anthropic` / `gemini` |
| `Model.Providers.<Id>.BaseUrl` | string | — | 基础地址（如 `https://api.openai.com/v1`），适配器拼接 `/chat/completions` |
| `Model.Providers.<Id>.ApiKey` | string | — | 支持 `${ENV}` 占位 |
| `Model.Providers.<Id>.Model` | string | — | 模型名 |
| `Model.Providers.<Id>.TimeoutSeconds` | int | 120 | 单次请求超时 |
| `Model.Providers.<Id>.MaxRetries` | int | 3 | 5xx 重试次数（指数退避） |
| `Model.Providers.<Id>.RateLimit.Rpm` | int | 0 | 每分钟请求限制，0 表示不限 |
| `Model.Providers.<Id>.RateLimit.Tpm` | int | 0 | 每分钟 token 限制，0 表示不限 |
| `Model.Providers.<Id>.Extra` | map | — | 扩展参数（如 `AuthHeader`、`AuthScheme`） |

## 10. 工具权限

## 11. 日志

日志由入口（CLI / 桌面端）启动时按 `Aha.Logging` **程序化配置**（`aha-core` 的 `LoggingSetup`）：

> 设计与取舍（为什么不用 `log4j2.xml`、为什么 Console 是 `ERROR`、序号与保留上限为何不可兼得）见 [LoggingDesign.md](../Design/LoggingDesign.md)。

| Appender | 目标 | 级别 |
|---|---|---|
| File | `Logging.File` | `Logging.Level` |
| Console | **stderr** | 固定 `ERROR` |

- Console 写 stderr 且只输出 `ERROR`：stdout 专供对话内容，INFO / WARN 日志若写入 stdout 会打断交互输入
- 日志目录不存在时自动创建；创建失败则退化为仅 console，**不阻断启动**
- 级别非法（如 `VERBOSE`）时退回 `INFO`，同样不阻断启动

**默认 INFO 已记录的关键事件**：

| 事件 | 记录内容 |
|---|---|
| 配置加载 | 实际读到的配置文件绝对路径 |
| 会话创建 / 关闭 | 会话 ID、模型、身份字符数 |
| 推理开始 | 会话 ID、流式 / 非流式、输入长度 |
| 工具调用 | 工具名、参数、输出字符数（失败记 `INFO`——用户拒绝授权属预期业务结果） |

需要完整的 LLM 请求 URL 时设为 `DEBUG`。

**切分规则**：单个日志文件超过 **10 MB** 即切分，切分文件与主文件同目录，命名为
`<基名>-yyyy-MM-dd-NN.log`（`AHA.log` → `AHA-2026-10-07-01.log`）：

- `yyyy-MM-dd` 取**切分发生时刻**的日期，序号按当天独立编号
- `NN` 为**补零**的两位序号，从 `01` 起递增（`01`、`02`…`10`）
- 切分文件**不压缩**，以便直接用编辑器打开排查
- 历史切分文件**不自动清理**，需要时自行删除（按日期已可辨识先后）

> **日志位置**：`Logging.File` 默认 `~/.aha/Log/AHA.log`；设置 `AHA_HOME` 后落在
> `$AHA_HOME/Log/`。目录不存在时自动创建；创建失败则退化为仅 console（不阻断启动）。

`Tools.AutoApprove` 在默认策略（`READ` 与 `NETWORK` 自动放行）之上追加权限：

```yaml
Aha:
  Tools:
    AutoApprove:
      - WRITE      # 文件写入无需交互确认
      # - EXECUTE
      # - ADMIN
      # - ALL      # 全部放行（谨慎）
```

### 交互式确认

`chat` 中遇到需授权的工具时会**暂停并询问**，而不是直接失败：

```
需要授权：file-write 请求 WRITE 权限
  path = CLAUDE.md
  content = # Test
  [y] 允许本次   [n] 拒绝   [a] 本次会话内始终允许 WRITE
授权>
```

| 输入 | 行为 |
|---|---|
| `y` | 允许本次调用 |
| `n` / 直接回车 | 拒绝（回车默认拒绝，避免误触放行） |
| `a` | 本次会话内始终允许**该权限**（不影响其他权限） |
| `Ctrl+C` / `Ctrl+D` | 视为拒绝，**不中断对话** |

拒绝后会在终端直接列出授权方式。

非交互场景（`run`）无确认余地，未加 `--yes` 时一律拒绝：

```bash
aha chat             # 逐个确认
aha chat --yes       # 自动授权 WRITE/EXECUTE/ADMIN，不再询问
aha run "..." --yes  # 单次推理同理
```

> `a` 的“始终允许”**只在当前进程内有效**，不写盘。
> 需要永久放行请配置 `Tools.AutoApprove`。
>
> 未获授权时抛出 `ToolExecutionException`（`PERMISSION_DENIED`），
> 错误信息会说明授权方式，不留下“不知道怎么办”的空白。
