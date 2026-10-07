# 排错指南

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
| v1.1.0 | 2026-10-06 | 构建期问题移至 [BuildGuide.md](BuildGuide.md)，本文件专注运行时与配置排错 | @ACANX |
| v1.2.0 | 2026-10-06 | 新增「常见误解」（模型自称其他厂商）与启动信息诊断 | @ACANX |
| v1.2.0 | 2026-10-07 | 日志文件名更正为 `AHA.log`；补指向 LoggingDesign.md | @ACANX |
| v1.3.0 | 2026-10-06 | 新增 `LLM_HTTP_422` 排查项；说明错误信息已含服务端响应体 | @ACANX |
| v1.4.0 | 2026-10-06 | 新增「首轮正常、第二轮 422」专项（多轮工具调用上下文丢失） | @ACANX |
| v1.5.0 | 2026-10-07 | 新增「终端与编码」专项（控制台代码页、中文乱码与符号降级） | @ACANX |
| v1.6.0 | 2026-10-07 | 符号降级补充列表符号 `•` | @ACANX |
| v1.7.0 | 2026-10-07 | 符号降级补充转圈帧（盲文 `⠋` 在 GBK 下不可编码） | @ACANX |

---

本文件聚焦**运行时与配置**问题。
构建期问题（编译、JPMS、JaCoCo、Maven 语法等）见 [BuildGuide.md](BuildGuide.md) 第 7 节。

## 1. 配置问题

| 问题 | 原因 | 解决 |
|---|---|---|
| `INVALID_FIELD_NAME` | YAML 字段非 PascalCase | 修改为 PascalCase（SQLite 表名/字段、`Extension.Settings` 键除外） |
| `PROVIDER_NOT_FOUND`（配置层） | `Model.yml` 的 `Default` 指向不存在的键 | 用 `aha provider list` 校对键名，或 `aha provider use <id>` 切换 |
| API Key 未生效 | 环境变量未设置或拼写错误 | `aha provider test <id>` 查看「所需环境变量」，据此设置 `AHA_API_KEY_*` |
| 中转站流式截断 | 响应缺少 `[DONE]` | 适配器已容忍，依据 `finish_reason` 收尾；如仍异常检查 `Extra` 鉴权配置 |

## 2. 运行时问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `PERMISSION_DENIED` | 工具需 `WRITE`/`EXECUTE`/`ADMIN` 且未授权 | CLI 加 `--yes`，或配置 `Tools.AutoApprove` |
| `LLM_HTTP_401` | API Key 缺失或错误 | 设置对应 `AHA_API_KEY_*` |
| `LLM_HTTP_404` | `BaseUrl` 路径不正确 | `BaseUrl` 填**基础地址**（如 `https://api.deepseek.com/v1`），适配器会自动拼接 `/chat/completions`；若已包含端点路径则不会重复拼接 |
| `LLM_HTTP_422` | 请求被服务端接受但内容不可处理。**具体原因写在响应体里，AHA 已一并输出**，形如 `流式请求失败: HTTP 422 {"error": {"message": "..."}}` | 按下述清单核察 |
| `会话不存在` | 会话 ID 不匹配或已关闭 | 重新 `createSession`，不要复用过期 ID |
| `NO_PROVIDER_FOR_ENDPOINT` | 使用了尚未实现的远程端点（如 `ssh://`） | 1.0 前请使用 `local://`；规划见 [RemoteAndProtocolDesign.md](../Design/RemoteAndProtocolDesign.md) |
| SQLite 在虚拟线程下报错 | JDBC 连接非线程安全 | 每会话单一连接；0.2 引入连接池（见设计文档风险表） |
| 工具调用无响应 | 未获授权的工具被静默拒绝 | 查看日志中的 `PERMISSION_DENIED` 记录，或 `aha tool list` 确认 `AUTO` 列 |

### 2.1 `HTTP 422` 专项排查

AHA 会把服务端返回的响应体一并输出，先看它说什么：

```
[error] 流式请求失败: HTTP 422 {"error": {"message": "Model Not Exist: deepseek-flash", "type": "invalid_request_error"}}
```

常见原因：

| 响应体关键词 | 原因 | 处理 |
|---|---|---|
| —（第二轮起 422，首轮正常） | **多轮对话丢失工具调用上下文**（0.1.0 早期版本的缺陷） | 升级后重新构建；旧会话已写入的坏记录会被自动丢弃。详见下文 |
| `Model Not Exist` / `model not found` | 模型名不被该端点支持 | 核对供应商文档；用 curl 试探同一 `BaseUrl` + 模型名 |
| `invalid_request_error` + 字段名 | 请求体含端点不支持的字段 | 若经中转站，检查 `ProviderConfig.Extra` 是否透传了额外字段 |
| `insufficient` / `quota` / `balance` | 额度或余额不足 | 充值或改用其他供应商 |
| `content` / `safety` | 内容审核拦截 | 调整输入；网关策略问题需联系服务方 |
| 空响应体 | 网关自行拦截（未透传上游错误） | 用 curl 直连同一 `BaseUrl` 对比，可定位是 AHA 还是网关的问题 |

**关键区分**：模型名“看起来像”某个官方名称并不代表它被支持。例如 `deepseek-flash`
并非 DeepSeek 官方模型（官方为 `deepseek-chat`、`deepseek-reasoner`）；
若经中转网关，该网关可能自行映射模型名，也可能直接拒绝。
用 `curl` 直连同一 `BaseUrl` 测试是判定归属的最快方式。

**“首轮正常、第二轮 422”专项**（0.1.0 早期版本的真实缺陷）：

工具调用信息（`tool_calls` / `tool_call_id`）未持久化，导致从历史恢复时请求体非法：

```json
{"role": "user",      "content": "列一下当前目录"}
{"role": "assistant", "content": ""}                 // tool_calls 丢失
{"role": "tool",      "content": "[...]"}            // tool_call_id 丢失 → 违反 OpenAI 协议
{"role": "user",      "content": "再总结一下"}
```

为何表现为“第二轮才报错”：首轮不加载历史，请求体总是合法；次轮起才回传历史，
丢失的工具上下文才暴露。**复杂任务更容易触发工具调用**，所以现象上像是“任务一复杂就 422”。

升级后 `message` 表会自动加列并重新持久化；**旧会话中已写入的坏记录会被自动跳过**
（无 `tool_call_id` 的 `tool` 消息、无工具调用且无正文的 `assistant` 消息）。
若仍报错，可开会话重试。

## 3. 日志与诊断

日志文件：`${AHA_HOME}/Log/AHA.log`，级别通过 `Aha.Logging.Level` 配置。
切分规则（超 10 MB → `AHA-yyyy-MM-dd-NN.log`）与设计取舍见 [LoggingDesign.md](../Design/LoggingDesign.md)。

```bash
# 查看当前日志级别
./mvnw -pl aha-cli exec:java -Dexec.args="config get Logging.Level"

# 使用独立数据目录调试，避免污染正式数据
export AHA_HOME=/tmp/aha-debug

# 查看数据库（表名为单数，字段为 snake_case）
sqlite3 $AHA_HOME/Data/Aha.db "select * from session;"
sqlite3 $AHA_HOME/Data/Aha.db "select role, substr(content,1,60) from message order by id desc limit 10;"
```

数据库表：`session`、`message`、`memory`。

## 4. 常见误解

### 模型自称是其他厂商的模型

问模型“你是什么模型”，它回答自己是 Claude / ChatGPT，**通常不是配置问题**：

- 大模型没有可靠的内省能力，无法真正读取自身部署标识
- 训练语料中“我是由 Anthropic / OpenAI 开发的”这类自述模板频率极高，模型倾向于复现
- 同一模型换个问法可能给出不同答案

**如何可靠确认实际模型**：

| 方法 | 说明 |
|---|---|
| 看 `aha` / `aha chat` 启动信息 | 直接显示供应商、模型与**实际请求端点** |
| 看服务端返回的 `model` 字段 | 服务端自己声明的，最可靠；若与请求不一致则说明网关改了路由 |
| 绕过 AHA 直接 curl | 用相同参数请求同一端点，对比返回 |

### 回答不符合预期

先看启动信息的“端点”与“模型”两行：

```
  供应商      DeepSeek
  模型       deepseek-chat
  端点       https://api.deepseek.com/v1/chat/completions
```

- 端点不符 → 检查 `Model.yml` 的 `BaseUrl`（应为**基础地址**，适配器自拼接端点路径）
- 模型不符 → 检查 `Model.yml` 中对应供应商的 `Model` 字段
- 供应商不符 → `aha provider list` 看 `<== 当前默认` 标记，用 `aha provider use <id>` 切换

## 5. 终端与编码

### 中文变乱码 / 部分符号显示为 `?`

Windows 控制台按**代码页**解释字节：中文环境为 936（GBK），英文环境常为 437 / 1252。
JVM 写 stdout 的编码与之一致（`stdout.encoding`），因此：

| 情形 | 表现 | 处理 |
|---|---|---|
| 中文环境（代码页 936） | 中文正常，但 `❯` `✓` `•` `⠋` **不在 GBK 内** → 显示为 `?` | AHA 会**自动降级为 ASCII**（`❯ ` → `> `、`✓` → `[ok]`、`• ` → `- `、转圈帧 → `\|/-\\`），无需处理 |
| 英文环境（代码页 437 / 1252） | **中文全部变 `?`** | AHA 启动时用 ASCII 提示；按下方切换控制台编码 |

切换控制台到 UTF-8：

```bat
chcp 65001
```

```powershell
[Console]::OutputEncoding = [Text.Encoding]::UTF8
```

```bash
export LANG=C.UTF-8        # Linux：locale 非 UTF-8 时
```

> **为什么 AHA 不自动执行 `chcp`**：PowerShell 5.1 会缓存 `[Console]::OutputEncoding`，
> 子进程里改代码页后它仍按旧编码解码，反而更乱。因此只探测、提示与降级，
> 是否切换交给用户。
>
> 切换后 AHA 的探测会得到 UTF-8，提示符与成功标记自动恢复为 `❯` / `✓`。

**自检**：AHA 启动时会把终端能力与输出编码写入日志（`Log/` 目录）：

```
终端能力: tty=true width=120 color=true {TERM=xterm-256color, COLORTERM=truecolor, depth=TRUECOLOR}
输出编码: UTF-8 cjk=true symbols=true
```

---

## 6. 自助排查顺序

1. `aha version` —— 确认版本
2. `aha config get Llm.DefaultProvider` —— 确认实际生效的配置（含 `Model.yml` 合并结果）
3. `aha provider list` —— 确认当前默认供应商
4. `aha provider test <id>` —— 确认 API Key 与端点配置
5. `aha tool list` —— 确认工具与权限
6. 仍未解决 → 查看 `${AHA_HOME}/Log/AHA.log`
