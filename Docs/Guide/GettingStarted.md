# 快速开始

**文档版本**：v1.7.0
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
| v1.1.0 | 2026-10-06 | 新增初始化步骤（`aha init`）；修正测试数与命令清单 | @ACANX |
| v1.2.0 | 2026-10-06 | 新增直接 `aha` 启动、运行时信息展示与项目根探测 | @ACANX |
| v1.3.0 | 2026-10-06 | 新增 Agent 身份（系统提示词）定义与加载说明 | @ACANX |
| v1.4.0 | 2026-10-06 | 运行目录 `keys/`→`Key/`（单数 + 大驼峰）；默认路径改为 `~/.aha` | @ACANX |
| v1.5.0 | 2026-10-07 | 5.1 新增会话内 `/memory` 查看与编辑身份的用法 | @ACANX |
| v1.6.0 | 2026-10-07 | 版本号统一为 0.1.0（构建期注入） | @ACANX |
| v1.7.0 | 2026-10-07 | 身份注入口径统一为「每个来源各自作为一条独立的 system 消息，不拼接」 | @ACANX |

---

## 1. 环境

- JDK 25 (LTS)
- Maven 4（通过 `./mvnw` 固定）或 Maven 3.9.x（兼容验证）

## 2. 构建

```bash
./mvnw clean verify
```

## 3. 初始化

首次使用执行一次，生成配置与目录结构（**无需手工编写配置文件**）：

```bash
dist/bin/Aha.sh init          # Linux / macOS
dist\bin\Aha.bat init         # Windows
```

交互模式下会列出内置供应商供选择，并可选择直接写入 API Key：

```
AHA 初始化

  配置目录  /home/me/.aha
  模型配置  /home/me/.aha/Model.yml

可用供应商（内置预设）：

  1) OpenAI             gpt-4o                 ${AHA_API_KEY_OPENAI}
  2) Anthropic          claude-sonnet-5-1      ${AHA_API_KEY_ANTHROPIC}
  3) Gemini             gemini-2.5-flash       ${AHA_API_KEY_GEMINI}
  4) DeepSeek           deepseek-chat          ${AHA_API_KEY_DEEPSEEK}
  5) BigModelCN         glm-4.6                ${AHA_API_KEY_BIG_MODEL_CN}
  6) Qwen               qwen-max               ${AHA_API_KEY_QWEN}

请选择默认供应商 [1-6]（直接回车使用 OpenAI）:
```

生成结果：

```
~/.aha/
├── Model.yml      # 供应商配置（权限 600）
├── Data/          # SQLite 会话与记忆
├── Key/           # 加密密钥库
├── Log/           # 日志
└── Extension/     # 扩展
```

### 非交互模式

供脚本 / CI 使用：

```bash
aha init --no-input                        # 只生成内置全量配置
aha init --provider DeepSeek               # 指定默认供应商
aha init --provider DeepSeek --api-key sk-xxx   # 直接写入密钥
aha init --force                           # 覆盖已有配置（默认幂等，不覆盖）
```

> 不写入 API Key 时，`Model.yml` 保留环境变量占位（如 `${AHA_API_KEY_DEEPSEEK}`），
> 设置环境变量后即可使用。

## 4. 运行 CLI

### 方式一：从源码运行（开发时）

```bash
./mvnw -pl aha-cli exec:java
```

### 方式二：从发行包运行（推荐）

```bash
./mvnw clean package         # 产出项目根 dist/
dist/bin/Aha.sh chat         # Linux / macOS
dist\bin\Aha.bat chat        # Windows
```

发行包内 `lib/` 已包含全部运行时依赖，无需另行准备 classpath。

在日常工作目录下直接运行 `aha` 即可进入对话（等价于 `aha chat`），无需额外子命令：

```bash
cd /path/to/your/project
aha                         # 直接进入交互式对话
```

启动时会显示实际连接信息，便于确认请求发往何处：

```
AHA 交互式对话（输入 exit / quit 退出）
  供应商      DeepSeek
  模型       deepseek-chat
  端点       https://api.deepseek.com/v1/chat/completions
  工作目录     /path/to/your/project
  项目根      /path/to/your/project
  会话       19ea64fb-857e-46b7-87f5-7fa5b591c7e6

aha>
```

> 模型自述（“你是什么模型”）**不可靠**，判断实际模型请看上述“供应商/模型/端点”，
> 或看服务端返回的 `model` 字段。

非交互环境（管道 / 脚本 / CI）下无参数会打印帮助，不会挂起等待输入。

## 5. 配置

### 5.1 定义 Agent 身份（角色）

在项目根创建 `AHA.md`（或 `AGENTS.md`）即可定义 Agent 的身份与行为约定，
文件内容会各自作为一条独立的 system 消息注入（不拼接成一整段）：

```markdown
<!-- 项目根/AHA.md -->
你是本项目的代码审查员，只做代码审查，不写新功能。

输出要求：
- 先给结论（通过 / 不通过）
- 再列问题，每条标注严重级别
```

启动时会显示实际生效的来源：

```
  供应商      DeepSeek
  模型       deepseek-chat
  身份来源     /path/to/project/AHA.md（63 字符）
```

查找顺序：从当前目录逐级向上找 `AHA.md` → `AGENTS.md`（就近优先），
再回退到用户级 `~/.aha/`，均无则用内置默认身份。
临时覆盖：`aha chat --system "..."` 或 `--system-file <path>`。

> 身份在**会话创建时**固化，同一会话内不重复解析；
> 但每轮请求仍需携带 system 消息（LLM API 无状态）。

在会话内可直接查看与编辑这份身份文件，不必离开 REPL：

```
/memory                      查看生效身份的正文与来源
/memory files                列出候选文件与查找顺序
/memory edit                 唤起外部编辑器（退出后自动刷新）
/memory append 记住：回复用中文    追加一行
/memory reload               用别的工具改过文件后刷新本会话
```

改动**下一轮即生效**（每轮都会重新拼 system 消息），不必重开会话。
编辑器按 `-Daha.editor` → `$VISUAL` → `$EDITOR` → 平台默认顺序选择，
例如 `export EDITOR="code -w"`。

### 5.2 模型供应商

模型供应商配置位于用户级目录（含 API Key，属凭据类配置）：

```
$AHA_HOME/Model.yml        已设置 AHA_HOME
~/.aha/Model.yml           未设置 AHA_HOME（默认）
```

日常管理：

```bash
aha provider list              # 列出供应商（含当前默认）
aha provider use DeepSeek      # 一键切换默认供应商
aha provider add MyProxy ...   # 新增（私有部署 / 中转站）
aha provider test DeepSeek     # 查看配置与所需环境变量
```

或直接设置环境变量（如 `AHA_API_KEY_DEEPSEEK`）后使用内置预设。

详见 [ConfigurationGuide.md](ConfigurationGuide.md)。

## 6. 实现现状（0.1.0）

已实现并可运行：

| 能力 | 组件 |
|---|---|
| LLM 适配 | `OpenAiAdapter`（含 DeepSeek/BigModelCN/Qwen/中转站）、`AnthropicAdapter`、`GeminiAdapter` |
| 推理引擎 | `AgentEngine`（工具调用循环）与流式版本 |
| 记忆 | `SqliteMemoryStore`（WAL） |
| 密钥 | `EncryptedFileSecretStore`（AES-256-GCM + PBKDF2） |
| 工具 | `file-read` / `file-write` / `file-list` / `http-get` / `shell-exec` |
| CLI | `init` / `chat` / `run` / `tool` / `provider` / `extension` / `config` / `version` |
| Agent 身份 | `AHA.md` / `AGENTS.md` → system 消息，可自定义角色 |

### 6.1 运行目录

数据默认落在 `$AHA_HOME`（未设置时为 `~/.aha`）：

```
$AHA_HOME/
├── Data/Aha.db        # SQLite（会话、消息、记忆）
├── Key/Aha.keystore   # 加密密钥库（如使用）
└── Log/AHA.log        # 日志
```

### 6.2 快速验证

无需 API Key 即可验证的命令：

```bash
./mvnw clean verify                              # 构建 + 全部测试 + 覆盖率门禁
./mvnw -pl aha-cli exec:java -Dexec.args="init --no-input --force"
./mvnw -pl aha-cli exec:java -Dexec.args="version"
./mvnw -pl aha-cli exec:java -Dexec.args="provider list"
./mvnw -pl aha-cli exec:java -Dexec.args="tool list"
./mvnw -pl aha-cli exec:java -Dexec.args="config get Llm.ModelFile"
```

需要 API Key 的命令：

```bash
export AHA_API_KEY_DEEPSEEK=sk-...
./mvnw -pl aha-cli exec:java -Dexec.args="run 用一句话介绍 AHA"
./mvnw -pl aha-cli exec:java -Dexec.args="chat"
```

### 6.3 下一步

- 供应商接入：见 [ProviderSetupGuide.md](ProviderSetupGuide.md)
- 工具与权限：见 [ToolUsageGuide.md](ToolUsageGuide.md)
- 遇到问题：见 [TroubleshootingGuide.md](TroubleshootingGuide.md)
