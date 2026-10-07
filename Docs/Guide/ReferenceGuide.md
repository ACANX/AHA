# 命令与配置参考

**文档版本**：v1.30.0
**状态**：冻结
**生效日期**：2026-10-06
**最后更新**：2026-10-07
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-06 | 初始版本：命令参数、配置项、环境变量集中参考 | @ACANX |
| v1.1.0 | 2026-10-06 | 补充日志配置项、`AHA_LOG_*` 环境变量、退出时会话提示 | @ACANX |
| v1.2.0 | 2026-10-06 | 默认路径改为 `~/.aha`；`keys`→`key` | @ACANX |
| v1.3.0 | 2026-10-06 | 登记 `aha secret` 命令 | @ACANX |
| v1.4.0 | 2026-10-07 | 扩展机制统一为 Extension 命名：模块 `aha-extension-api`、包名、类名、配置段与描述符 | @ACANX |
| v1.5.0 | 2026-10-07 | 新增会话内命令（`/help` `/config` `/context` `/exit`）说明与输出示例 | @ACANX |
| v1.6.0 | 2026-10-07 | 新增终端渲染与降级说明；`chat` / `run` 补充 `--no-color` | @ACANX |
| v1.7.0 | 2026-10-07 | §2.5 补充输出编码探测与符号降级（`❯` / `✓` 在 GBK 下退化） | @ACANX |
| v1.8.0 | 2026-10-07 | §2.5 补充 Markdown 轻量渲染规则表 | @ACANX |
| v1.9.0 | 2026-10-07 | §2.5 补充瞬时状态行行为表 | @ACANX |
| v1.10.0 | 2026-10-07 | §2.5 Markdown 规则表补充粗体 / 斜体与误判防范说明 | @ACANX |
| v1.11.0 | 2026-10-07 | §3.2 补全会话内命令表与 `/clear`、`/tool` 输出示例 | @ACANX |
| v1.12.0 | 2026-10-07 | §3.2 新增 `/compact`、`/autocompact` 命令与输出示例 | @ACANX |
| v1.13.0 | 2026-10-07 | 新增 §3.2.1 `/memory`（Agent 身份文件查看与编辑） | @ACANX |
| v1.14.0 | 2026-10-07 | 补版本号注入机制说明 | @ACANX |
| v1.15.0 | 2026-10-07 | 修正留存 API 名：`LineReader.printAbove` | @ACANX |
| v1.16.0 | 2026-10-07 | 补 `chat` 的流式行为：流式期间仍可输入，Ctrl+C 只打断本轮 | @ACANX |
| v1.17.0 | 2026-10-07 | 补底部状态行的行为与降级说明 | @ACANX |
| v1.18.0 | 2026-10-07 | 补输入行分隔线与工具区块展示 | @ACANX |
| v1.19.0 | 2026-10-07 | 状态行文案收敛为「转圈帧 + 阶段」 | @ACANX |
| v1.20.0 | 2026-10-07 | `/prompt` 改名 `/memory` 并补写入目标；输入区补补全与实时着色 | @ACANX |
| v1.21.0 | 2026-10-07 | `/memory` 说明补自动创建与分节展示 | @ACANX |
| v1.22.0 | 2026-10-07 | 补注入顺序与新增兼容文件的方式 | @ACANX |
| v1.23.0 | 2026-10-07 | 注入顺序改为「同层全部候选 + 层级叠加」，并说明启动时会打印 | @ACANX |
| v1.24.0 | 2026-10-07 | 补默认候选含 `CLAUDE.md`、去重规则与未加载兼容文件的提示 | @ACANX |
| v1.25.0 | 2026-10-07 | 身份注入口径统一为「每个来源各自作为一条独立的 system 消息，不拼接」 | @ACANX |
| v1.26.0 | 2026-10-07 | `Logging.File` 说明改为切分命名规则；日志文件名为 `AHA.log` | @ACANX |
| v1.27.0 | 2026-10-07 | `Aha.Logging.Level` 默认值更正为 `DEBUG` | @ACANX |
| v1.28.0 | 2026-10-07 | 启动信息样本更新为实际输出：顶栏与右侧 ASCII 标志并排、细节整宽在下 | @ACANX |
| v1.29.0 | 2026-10-07 | `aha chat` 新增 `--logo` 选项（auto / pixel / ascii / off） | @ACANX |
| v1.30.0 | 2026-10-08 | 版本号说明改为「已发行版本 / dev 上版本 / 改版本号不止根 POM」，并指向 ReleaseProcess.md 第 2 节 | @ACANX |

---

## 1. 命令总览

| 命令 | 说明 | 0.1 状态 |
|---|---|---|
| `aha` | 无参数：交互终端进入对话，非交互打印帮助 | ✅ |
| `aha init` | 初始化配置（生成 `Model.yml` 与目录结构） | ✅ |
| `aha chat` | 启动交互式对话 | ✅ |
| `aha run <input>` | 单次推理并输出结果 | ✅ |
| `aha tool list` | 列出工具（含权限与放行状态） | ✅ |
| `aha tool invoke <name>` | 调用工具 | ✅ |
| `aha provider list` | 列出供应商 | ✅ |
| `aha provider use <id>` | 切换默认供应商 | ✅ |
| `aha provider add <id>` | 新增 / 覆盖供应商 | ✅ |
| `aha provider remove <id>` | 删除供应商 | ✅ |
| `aha provider test <id>` | 查看供应商配置（不发起请求） | ✅ |
| `aha config get <key>` | 读取配置 | ✅ |
| `aha config set <key> <value>` | 设置配置（提示手工编辑） | ⚠️ 占位 |
| `aha config edit` | 显示配置文件路径 | ✅ |
| `aha secret set/get/delete/list` | 密钥库管理（加密存储 API Key） | ✅ |
| `aha extension list/enable/disable/info` | 扩展管理 | ⛔ 自 0.3 提供 |
| `aha version` | 版本信息 | ✅ |
| `aha --help` / `-h` | 帮助 | ✅ |
| `aha --version` / `-V` | 版本 | ✅ |

> **版本号**：`-V` 输出的版本在构建时由 Maven 注入（`AhaCli` 的 `@Command(version=...)`）——
> 由资源过滤写入 `version.properties`，运行时读取，**根 `pom.xml` 是唯一来源**。
> 已发行的版本见 release 页（当前最新为 `0.1.0`，tag `0.1.0`；`dev` 上工作在 `0.1.1`）。
> 改版本号时**不止根 POM**：六个子模块的 `<parent><version>` 也要同步，
> 否则构建会成功但产物仍是旧版本号（详见 [ReleaseProcess.md](../DevSpec/ReleaseProcess.md) 第 2 节）。

---

## 2. 全局行为

### 2.1 无参数行为

```bash
aha
```

| 环境 | 行为 |
|---|---|
| 交互式终端（`System.console() != null`） | 等价于 `aha chat`，直接进入对话 |
| 非交互（管道 / 脚本 / CI） | 打印帮助后退出 |

**设计理由**：与 `node` / `python` / `claude` / `aider` 惯例一致；交互时省去输入命令，
非交互时不挂起等待输入。

### 2.2 退出码

| 码 | 含义 |
|---|---|
| `0` | 成功 |
| `1` | 失败（如 `aha run` 调用失败、`runner` 抛异常、启动脚本环境不满足） |
| `2` | picocli 参数解析错误（未知命令 / 缺少必填参数） |

### 2.3 标准选项

由 picocli `mixinStandardHelpOptions = true` 提供（全部 22 个子命令均可使用）：

| 选项 | 说明 |
|---|---|
| `-h`, `--help` | 打印帮助（如 `aha provider add --help`） |
| `-V`, `--version` | 打印版本 |

### 2.4 退出时的会话提示

`chat` 与 `run` 在退出时（含异常与 Ctrl+C）输出：

```
会话已保存：863fa425-6bab-4f75-9f5b-3c55c61961fd
续接本次会话：aha chat --session 863fa425-6bab-4f75-9f5b-3c55c61961fd
```

会话内容已持久化到 SQLite，用该 ID 即可从上次位置继续。

---

### 2.5 终端渲染与降级

渲染层探测**能力**而非平台，不针对具体终端品牌做适配。覆盖 Windows CMD / PowerShell /
Windows Terminal / WSL / Linux 及第三方终端（Tabby、Xshell、MobaXterm 等）与 tmux / screen。

| 维度 | 依据 | 降级行为 |
|---|---|---|
| 是否 TTY | JLine `Terminal.getType()` + `System.console().isTerminal()` | 非 TTY（管道 / CI / 重定向）→ 纯文本，**不输出 ANSI，也不插入硬换行** |
| 是否着色 | `--no-color`、`NO_COLOR` | 关闭颜色，保留结构 |
| 色彩深度 | `COLORTERM` → `TERM` 含 `256color` → Windows 控制台标识（`WT_SESSION` / `ConEmuANSI` / `ANSICON` / `TERM_PROGRAM`） | 目前只用基础 SGR（16 色），深度仅决定「是否着色」 |
| 宽度 | JLine `Terminal.getWidth()` → `COLUMNS` | 未知则不折行；未知宽度时不能按字符数猜 |
| 输出编码 | `Console.charset()` → `stdout.encoding` → `native.encoding` | 实测能否表示 CJK 与装饰符号：不值则降级（`❯ ` → `> `、`✓ ` → `[ok]`）；不能表示中文时用 ASCII 提示切换控制台编码 |

**两条纪律**：

1. **折行按显示宽度**（wcwidth）：汉字 / 全角标点 / emoji 占 2 列，按 `String.length()`
   计算必然错行
2. **不发送光标控制序列**（定位 / 清屏 / 行内重写）：只用基础 SGR 与换行。
   第三方终端与多路复用器对光标控制的支持不一致，是真正的兼容风险点；
   内联形态天然避开了全屏备用缓冲区

长时间的工具输出会被截断（参数 500 字符、结果 2000 字符），避免一次文件读取淹没对话。

**Markdown 轻量渲染**：模型输出按 Markdown 渲染，只处理四项：

| Markdown | 渲染 |
|---|---|
| `# 标题` 至 `###### 标题` | 丢弃 `#` 标记，整行**加粗** |
| `- ` / `* ` / `+ ` | 换成醒目符号 `• `（编码不支持时保留原符号）；标记着色 |
| `1. ` | 保留编号；标记着色 |
| `> ` 引用、`---` / `***` 分隔线 | 整行**弱化** |
| ` ``` ` 围栏代码 | 围栏标记弱化，**内部原样输出**（不做任何解析） |
| `` `code` `` | 去掉反引号，内容着色 |
| `**粗体**` / `__粗体__` | **加粗** |
| `*斜体*` / `_斜体_` | *斜体* |
| `***粗斜***` | **加粗**且*斜体* |

**为什么不会误判**（采用 CommonMark 侧翼规则的子集）：

| 定界符 | 开定界符要求 | 闭定界符要求 |
|---|---|---|
| `*` | 后一字符非空白 | 前一字符非空白 |
| `_` | 后一字符非空白，且**前一字符非字母数字** | 前一字符非空白，且**后一字符非字母数字** |

因此 `2 * 3 * 4` 仍是乘法，`snake_case_name` 仍是标识符。连续 4 个及以上定界符按字面量输出。

> 删除线（`~~`）**不处理**：它需要 SGR 9，支持面比加粗 / 斜体窄得多。
>
> 斜体用 SGR 3，部分老终端（如旧版 conhost）会忽略它而按普通文本显示——
> 与光标控制序列不同，**忽略是无害的**，不会弄乱内容或对齐。
>
> 粗体 / 斜体**不处理**：`*` 既可能是强调也可能是列表，歧义大、收益低。
>
> 围栏代码内部原样输出是刻意的——代码里的 `#`、反引号、`-` 若被当成 Markdown，
> 读者会被错误高亮误导。

**等待反馈（状态行）**：等待期间显示瞬时状态行，写正文前即清除：

```
⠋ 等待响应…  1.2s  deepseek-chat
⠹ 执行工具 shell-exec…  3.4s  deepseek-chat · 512 tok
```

| 行为 | 说明 |
|---|---|
| 出现时机 | 只在「等待中」：首次响应到达前、工具执行期间——这两段无输出最易让人以为卡死 |
| 重绘手段 | 只用 `\r`（回车），不使用光标定位 / 清屏序列 |
| 自动关闭 | 非 TTY（管道 / CI）、终端宽度未知、`--no-color` 无关（与颜色独立） |
| `tok` 含义 | 本轮累计**输出** token（来自 `UsageEvent`）；精确值见每轮 `[usage]` 行 |
| 帧降级 | 编码不支持盲文 `⠋` 时用 `\|/-\\`（GBK 不支持盲文） |

> 与流式正文**同时**显示的常驻状态行尚未实现（`LineReader` 未暴露其内部 `Display`）；
> 届时本状态行升级为常驻形态，调用点不变。

**控制台代码页**：Windows 控制台按代码页（中文环境 936）解释字节，代码页之外的字符会变成
`?`。AHA 不自动 `chcp`（PowerShell 5.1 会缓存 `[Console]::OutputEncoding`，子进程改代码页
反而更乱），而是探测后**降级符号**（`❯ ` → `> `、`✓` → `[ok]`、`• ` → `- `），
必要时用 ASCII 提示用户。详见 [TroubleshootingGuide.md](TroubleshootingGuide.md) 第 5 节。

---

## 3. 命令详解

### 3.1 `aha init`

首次使用执行一次，生成模型配置与运行目录。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `--provider <id>` | string | 否 | 默认供应商 ID（内置键名，如 `DeepSeek`） |
| `--api-key <key>` | string | 否 | API Key；不提供则写入环境变量占位符 |
| `--no-input` | flag | 否 | 非交互模式，直接生成内置全量配置 |
| `--force` | flag | 否 | 覆盖已存在的配置 |

**生成内容**

```
$AHA_HOME（或 ~/.aha）/
├── Model.yml          模型供应商配置（权限 rw-------）
├── data/              会话与记忆（SQLite）
├── key/               密钥存储
└── log/               日志
```

**行为细节**

- 交互模式（`--no-input` 未指定且存在控制台）：列出内置供应商供序号 / 键名选择，
  用 `readPassword()` 读取 API Key（不回显），不输入则保留环境变量占位符
- 非交互模式（`--no-input` 或无控制台）：直接生成内置全量配置，不阻塞
- **幂等**：配置已存在时提示，需 `--force` 才覆盖
- 空 `--api-key` 时**沿用配置中已有占位符**，而不是按显示名推导
  （`OpenAI` → `AHA_API_KEY_OPEN_AI` 这类推导会出错）

```bash
# 交互式
aha init

# 非交互，指定供应商与 Key
aha init --provider DeepSeek --api-key sk-xxxxx

# 非交互，仅生成占位配置（Key 走环境变量）
aha init --no-input

# 重新生成
aha init --force
```

### 3.2 `aha chat`

交互式对话，使用 JLine 读取输入。会话内用 `/help` 查看命令，`exit` / `quit` / `/exit` / Ctrl+D 退出。

**流式期间仍可输入**：模型还在输出时，你可以继续敲下一条消息（它会排队，本轮结束后执行），
也可以按 `Ctrl+C` **只打断本轮**——已生成的部分仍会保留在会话历史里，且不会退出会话。
`Ctrl+C` 在空闲时只清空当前输入行（与 shell 一致）。

**输入区**：输入行由上下两条分隔线框住，行首不加 `>` 之类的标记。
分隔线为高亮紫色。

输入以 `/` 开头时会**实时着色**：绿色表示命令已支持，红色表示未知；
按 `Tab` 弹出候选菜单（含用法说明），支持模糊匹配（如 `/cmpct` 也能匹配到 `/compact`）。
因此不必等到回车才知道命令拼得对不对。
下方那条线由状态行提供，因此状态行是**常驻**的：等待期间显示 `⠋ 阶段`（转圈帧实时变化），
回合结束时显示模型名。它只回答「现在在干什么」——耗时与输出规模由工具区块的末行给出。

**工具调用的区块展示**：每次读文件 / 写文件 / 执行命令 / 发请求都是一个带背景色带的区块，
颜色按操作的后果区分（读取、写入、执行、网络四类），结果行按成败着色，
输出只显示前 3 行并标注「还有 N 行」。

> 状态行依赖终端滚动区域；终端不支持时该行与下分隔线自动不显示（不影响其他功能），
> 工具区块同时退化为 `[读取] file-read  path` 这样的结构标记。
> 伪终端 / 管道等行数未知的场景下同样如此。

| 参数 | 类型 | 说明 |
|---|---|---|
| `--provider <id>` | string | 供应商 ID |
| `--model <name>` | string | 模型名 |
| `--session <id>` | string | 复用已有会话 ID（**身份设定沿用该会话，不重新解析文件**） |
| `--system <text>` | string | 系统提示词文本（覆盖文件加载） |
| `--system-file <path>` | path | 系统提示词文件（覆盖约定文件） |
| `--no-color` | flag | 关闭 ANSI 颜色（也受 `NO_COLOR` 影响） |
| `--logo` | MODE | 启动标志：`auto`（默认）/ `pixel` / `ascii` / `off` |

**启动信息**

```
AHA 交互式对话（/help 查看命令，exit / quit 退出）
  供应商   DeepSeek                                                                   \   |   /
  模型     deepseek-chat                                                               \  |  /
  端点     https://api.deepseek.com/v1/chat/completions                             ____\ | /____
  工作目录 /path/to/project/src                                                    /     \|/     \
                                                                                  |    .-"""-.    |
                                                                                  |   /  o o  \   |
                                                                                  |  |    ^    |  |
                                                                                  |   \  '-'  /   |
                                                                                  |    '-...-'    |
                                                                                   \             /
                                                                                    '--_______--'
                                                                                       |=====|
                                                                                       | AHA |
                                                                                       '-----'
  项目根   /path/to/project
  全局记忆 /home/x/.aha/AHA.md
  身份来源 内置默认身份
  会话     06ca3fca-7421-4f5f-94f0-fa8c971ead07
```

**会话内命令**

输入以 `/` 开头、且命令名只由字母 / 数字 / 连字符组成时，按会话内命令处理；
否则作为普通消息发给模型（因此 `/usr/bin/ls` 不会被误判为命令）。

| 命令 | 说明 |
|---|---|
| `/help` | 列出可用命令 |
| `/config` | 查看当前生效配置与来源 |
| `/context` | 查看本轮上下文 |
| `/session` | 查看会话 ID、身份与续接方式 |
| `/model [名称]` | 查看或切换本会话模型 |
| `/clear` | 清空本会话上下文 |
| `/tool [名称]` | 查看工具列表或详情 |
| `/compact [保留条数]` | 压缩上下文（模型摘要 + 消息替换） |
| `/autocompact [阈值\|off]` | 设置自动压缩阈值，如 `/autocompact 285k` |
| `/memory [view\|files\|edit\|append\|reload]` | 查看或编辑 Agent 身份文件（`AGENTS.md` / `AHA.md`） |
| `/exit` | 结束会话 |

`/clear` 输出示例（只删历史消息，会话与身份保留）：

```
上下文已清空
  已删除  12 条消息
  会话    06ca3fca-7421-4f5f-94f0-fa8c971ead07（会话记录与身份设定保留）
  说明    已存储的记忆条目不受影响
```

`/tool` 输出示例（经 `Aha.Tools.Enabled` 过滤后的可调用集合）：

```
工具（5 个启用）
  file-read      READ     AUTO      读取文件
  file-write     WRITE    CONFIRM   写入文件
  file-list      READ     AUTO      列出目录
  http-get       NETWORK  AUTO      HTTP GET
  shell-exec     EXECUTE  CONFIRM   执行命令

用法  /tool <名称>  查看权限与参数详情
```

`/compact` 输出示例（保留条数默认 10，`/compact 4` 可指定）：

```
上下文已压缩
  摘要  812 字符，由 deepseek-chat 生成
  消息  42 条 → 12 条（摘要 1 条 + 保留 11 条）
  估算  约 18630 tokens → 约 4210 tokens
  预览  用户要求实现 /compact 与 /autocompact…
  说明  摘要已作为系统消息写回历史开头，下一轮即生效
```

> 摘要生成失败或返回为空时，AHA 会报错并**保持历史不变**。
> 保留条数是期望值：若切割点落在工具调用与其结果之间，会继续前移，
> 以免构造出以 `tool` 消息开头的历史（服务端会直接拒绝）。

`/autocompact` 输出示例：

```
自动压缩已开启
  阈值  285000 tokens
  当前  约 1240 tokens（含系统提示词）
  触发  每轮回答后估算上下文，达到阈值即自动压缩
  保留  最近 10 条消息
  关闭  /autocompact off
```

`/autocompact` 接受纯数字及 `k` / `m` 后缀（`285k` = 285000、`1M` = 1000000），
写入时不能带空格（`285 k` 无效）。阈值存在会话配置里，
`aha chat --session <id>` 续接后仍然生效。

### 3.2.1 `/memory`：Agent 身份文件

AHA 启动时把每个身份文件**各自**作为一条独立的 `system` 消息注入（不拼接成一整段），等价于 Claude Code 的 `CLAUDE.md`。
查找顺序由 `Aha.Agent.PromptFiles` 决定（默认 `AHA.md` → `AGENTS.md`），
从工作目录逐级向上（就近优先），再查用户级 `$AHA_HOME`（或 `~/.aha`）。

| 子命令 | 作用 |
|---|---|
| `/memory` | 查看生效身份的**正文**：用户级与项目级分节列出，全局在前 |
| `/memory files` | 列出全部候选文件及查找顺序 |
| `/memory edit` | 唤起外部编辑器（文件不存在时先建骨架），退出后自动刷新 |
| `/memory append <文本>` | 追加上去（**默认写用户级**） |
| `/memory reload` | 重新读取文件并刷新本会话 |

**写入目标**：

| 目标 | 写法 | 落到哪里 |
|---|---|---|
| 用户级（默认） | `/memory append 一律用简体中文` | `$AHA_HOME`（默认 `~/.aha`）下的 `AHA.md`，不存在则新建 |
| 项目级 | `/memory append --project 用 Maven 构建` | 项目根下已有的身份文件；没有则建 `AGENTS.md` |
| 指定路径 | `/memory append --file ~/notes.md 内容` | 指定文件 |
| 指定文件名 | `/memory append --name AGENTS.md 内容` | 换用 `AGENTS.md` 而不是 `AHA.md` |

> **为什么默认写用户级**：会话里的临时追加多半是个人偏好（「一律用简体中文」这类）。
> 写进项目的 `AGENTS.md` 会被提交、影响所有协作者，不该是顺手一敲的默认结果。
> 项目约定建议直接编辑文件或走 PR。

> **用户级与项目级是叠加生效的**：两者都会注入，顺序为「用户级 → 项目级」。
> 因此写用户级不会被项目级遮蔽。
>
> **注入顺序**（越靠后越具体）：
> 1. 用户级目录（`~/.aha`）下**全部**候选，按 `Agent.PromptFiles` 顺序
> 2. 项目级：从工作目录向上，取**第一个命中目录**下**全部**候选，同样按该顺序
> 3. 一份身份文件都没有时，才用配置里的 `Agent.SystemPrompt`
> 4. 运行环境块永远在最后
>
> 同一层级内多个候选**都会生效**（`AHA.md` 与 `AGENTS.md` 并存时都注入）。
>
> 每个来源**各自成条**：系统提示词、各身份文件、运行环境块分别是一条独立的 `system` 消息，
> 不拼接成一整段——这样模型才能区分内容来自哪个文件。
> `aha chat` 启动时会把这份顺序逐条打印出来，不必猜。
>
> 默认候选：`AHA.md` → `AGENTS.md` → `CLAUDE.md`（含 Claude Code 兼容）。
> 新增其它兼容文件只要加到 `Aha.yaml` 的 `Agent.PromptFiles` 即可，不必改代码。
> 内容完全相同的文件只注入一次。
>
> 项目里若存在**未列入候选**的已知兼容文件（`GEMINI.md`、`.cursorrules` 等），
> 启动时会明确提示「发现但未加载」并给出加入方法——不会静默忽略。
>
> 启动 `aha chat` 时若用户级 `AHA.md` 不存在，会**自动创建**一个骨架 —— 全局记忆有落点，
> 不必先手工建文件。它只会在用户级目录创建，**不会碰项目目录**。

`/memory` 输出示例：

```
身份
  来源  /path/to/project/AGENTS.md
  字符  5387

你是 AHA（Agent Harness）驱动的编码助手……
```

`/memory edit` 输出示例：

```
编辑器  code -w
文件    /path/to/project/AGENTS.md
编辑器已退出（退出码 0）
刷新    5120 字符 → 5387 字符
来源    /path/to/project/AGENTS.md
说明    下一轮请求即生效（system 消息每轮重新拼装）
```

> **生效时机**：身份文本在建会话时固化进 `SessionConfig`，但每轮请求都会重新拼
> `system` 消息，因此 `/memory append`、`/memory edit`、`/memory reload` 都会
> **立即回写会话配置，下一轮即生效**，不必重开会话。
> 反之，直接用别的编辑器改文件**不会**影响已有会话，需要 `/memory reload`。

> **编辑器选择**：`-Daha.editor` → `$VISUAL` → `$EDITOR` → 平台默认
> （Windows `notepad`，macOS `open -W -t`，其余 `nano` / `vim` / `vi`）。
> 只选阻塞式命令，否则 AHA 无法知道何时恢复终端。
> 例：`export EDITOR="code -w"` 或 `java -Daha.editor="code -w" -m ...`。

> **权限边界**：身份文件定义 Agent 自身的行为约定，因此 `/memory` 系列
> **只由用户输入触发**——会话内命令不做模型调用分派，模型无法自行改写自己的指令。

> `/model` **只改模型，不改供应商**——会话配置固化的只是模型名；
> 切换供应商用进程级的 `aha provider use <id>`。传供应商 ID 时 AHA 会明确提示。

`aha config get` 与会话内 `/config` 共用取值逻辑，但作用范围不同：

| | 作用范围 | 反映的内容 |
|---|---|---|
| `aha config get <key>` | 进程级配置 | 主配置 `Aha.yaml` 中的单个键 |
| `/config` | 当前会话 | 全部有效配置 + 来源（主配置 / 模型配置 / `AHA_HOME`） |

`/config` 输出示例：

```
配置来源
  主配置      /path/to/project/Aha.yaml（不存在，当前使用内置默认 AhaDefault.yaml）
  模型配置     /home/me/.aha/Model.yml
  环境变量     AHA_HOME 未设置

有效配置
  Llm.DefaultProvider          DeepSeek
  Llm.ModelFile                /home/me/.aha/Model.yml
  Memory.Path                  /home/me/.aha/Data/Aha.db
  Memory.MaxContextEntries     100
  Logging.Level                DEBUG
  ...
```

> 路径类字段会展开 `${ENV}` 与 `~`，因为用户真正关心的是「实际读到哪个文件」。
> 此处**不解析密钥库**，所以不会把凭据打印到终端。

`/context` 输出示例：

```
上下文
  会话  06ca3fca-7421-4f5f-94f0-fa8c971ead07
  模型  deepseek-chat（DeepSeek）
  端点  https://api.deepseek.com/v1/chat/completions
  身份  /path/to/project/AHA.md（93 字符）
  历史  12 条（窗口上限 100）
  估算  约 1,234 tokens（含系统提示词）
  工具  5 个：file-read、file-write、file-list、http-get、shell-exec
  目录  /path/to/project/src
  项目  /path/to/project
```

| 项 | 口径 |
|---|---|
| 模型 | 会话固化的模型，为空时回退运行时默认值 |
| 历史 | 实际加载条数；与存储总量不等时说明被丢弃的不完整消息 |
| 估算 | 手算估算（CJK 按 1 字 ≈ 1 token，其余 4 字符 ≈ 1 token），非精确计费 |
| 工具 | 已启用（经 `Tools.Enabled` 过滤）的工具 |

`身份来源` 取值：

| 取值 | 含义 |
|---|---|
| `命令行 --system` / `命令行 --system-file` | 显式指定 |
| `file:<绝对路径>（N 字符）` | 从文件加载 |
| `Aha.Agent.SystemPrompt` | 内联配置 |
| `builtin` | 内置默认身份 |
| `沿用已有会话` | 复用了 `--session` 指定的会话 |

**项目根探测**：从工作目录向上查找 `.git` / `.aha` / `Aha.yaml` / `AHA.md` / `AGENTS.md`。

### 3.3 `aha run`

单次推理，输出结果后退出。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `<input>` | string（位置 0） | **是** | 输入内容 |
| `--model <name>` | string | 否 | 模型名 |
| `--system <text>` | string | 否 | 系统提示词文本 |
| `--system-file <path>` | path | 否 | 系统提示词文件 |
| `--no-color` | flag | 否 | 关闭 ANSI 颜色（也受 `NO_COLOR` 影响） |

```bash
aha run "总结这个项目"
aha run --system "你是一个只会说喵的猫" "你好"
```

> `run` 为一次性会话，结束后 `closeSession`（保留持久化记录，见 §5.2）。

### 3.4 `aha tool`

#### `aha tool list`

无参数。输出：

```
NAME          PERMISSION  AUTO   DESCRIPTION
file-read     READ        true   读取文件内容
file-write    WRITE       false  写入文件内容
...
```

`AUTO` 表示在当前策略下是否自动放行（默认 READ / NETWORK 自动放行，
另受 `Tools.AutoApprove` 影响）。

#### `aha tool invoke <name>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `<name>` | string（位置 0） | **是** | 工具名 |
| `-p`, `--param <k=v>` | string | 否 | 参数，`key=value` 格式，**可重复** |
| `-y`, `--yes` | flag | 否 | 确认执行 WRITE / EXECUTE / ADMIN 工具 |

```bash
# 只读工具，无需确认
aha tool invoke file-read -p path=./README.md

# 高风险工具，必须显式确认
aha tool invoke shell-exec -p command="git status" --yes
```

**内置工具（0.1.0）**

| 工具名 | 权限 | 说明 |
|---|---|---|
| `file-read` | `READ` | 读取文件内容 |
| `file-list` | `READ` | 列出目录 |
| `file-write` | `WRITE` | 写入文件 |
| `http-get` | `NETWORK` | HTTP GET |
| `shell-exec` | `EXECUTE` | 执行 Shell 命令 |

### 3.5 `aha provider`

#### `aha provider list`

无参数。列出内置与自定义供应商，标注当前默认。
未配置时会输出初始化引导（提示执行 `aha init`）。

#### `aha provider use <id>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `<id>` | string（位置 0） | **是** | 供应商 ID |

写入 `Model.yml` 的 `Model.Default`。

#### `aha provider add <id>`

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `<id>` | string（位置 0） | **是** | — | 供应商 ID（PascalCase，如 `DeepSeek`） |
| `--adapter <id>` | enum | **是** | — | `openai-compatible` / `anthropic` / `gemini` |
| `--base-url <url>` | string | **是** | — | **基础地址**（不含端点路径，见下方提示） |
| `--model <name>` | string | **是** | — | 模型名 |
| `--api-key <key>` | string | 否 | `${AHA_API_KEY_<ID>}` | API Key |
| `--timeout <sec>` | int | 否 | `120` | 超时秒数 |
| `--max-retries <n>` | int | 否 | `3` | 最大重试次数 |
| `--rpm <n>` | int | 否 | `0` | 每分钟请求上限，`0` 表示不限 |
| `--default` | flag | 否 | — | 同时设为默认供应商 |

> **`--base-url` 语义（易错点）**：这是**基础地址**，适配器负责拼接端点路径。
> 例如 OpenAI 兼容协议填 `https://api.deepseek.com/v1`，
> AHA 会请求 `https://api.deepseek.com/v1/chat/completions`。
> 若填成完整的 `.../v1/chat/completions`，适配器会识别后缀而不再重复拼接。

```bash
# 接入中转站
aha provider add Relay --adapter openai-compatible \
  --base-url https://relay.example.com/v1 \
  --model gpt-4o --api-key sk-xxx --default
```

**适配器类型**

| 值 | 协议 | 端点拼接 |
|---|---|---|
| `openai-compatible` | OpenAI Chat Completions | `{BaseUrl}/chat/completions` |
| `anthropic` | Anthropic Messages | `{BaseUrl}/v1/messages` |
| `gemini` | Google Gemini | `{BaseUrl}/v1beta/models/{model}:generateContent` |

#### `aha provider remove <id>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `<id>` | string（位置 0） | **是** | 供应商 ID |
| `-y`, `--yes` | flag | 否 | 确认删除 |

#### `aha provider test <id>`

查看指定供应商解析后的配置，**不发起网络请求**。

### 3.6 `aha config`

#### `aha config get <key>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `<key>` | string（位置 0） | **是** | 配置键 |

**支持的键（0.1.0 共 8 个）**

| 键 | 说明 |
|---|---|
| `Llm.DefaultProvider` | 默认供应商键名 |
| `Llm.ModelFile` | 模型配置文件**解析后的绝对路径**（含存在性提示） |
| `Llm.Fallback.Provider` | 兜底供应商 |
| `Llm.Fallback.Model` | 兜底模型 |
| `Memory.Storage` | 记忆存储类型 |
| `Memory.Path` | 记忆数据库路径 |
| `Logging.Level` | 日志级别（`TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR`/`FATAL`/`OFF`） |
| `Logging.File` | 日志文件路径（超 10 MB 切分为 `<基名>-yyyy-MM-dd-NN.log`） |
| `Extension.Enabled` | 扩展总开关 |

未知键返回 `<未知配置键: XXX>`。

> `Llm.ModelFile` 输出的是**解析后的绝对路径**而非原始配置值（如 `${AHA_HOME:-~/.aha}/Model.yml`），
> 因为用户真正需要知道的是「实际读到哪一个文件」。文件不存在时追加提示。

#### `aha config set <key> <value>`

0.1 为占位实现：仅提示手工编辑 `./Aha.yaml`，不实际写入。

#### `aha config edit`

打印项目配置文件 `./Aha.yaml` 的绝对路径。

### 3.7 `aha extension`

`list` / `enable` / `disable` / `info` 四个子命令在 0.1 均为占位，
统一输出 `扩展运行时自 AHA 0.3 起提供`，退出码 `0`。

### 3.8 `aha version`

打印版本号。

---

## 4. 配置文件

### 4.1 文件位置与优先级

| 文件 | 位置 | 说明 |
|---|---|---|
| `Aha.yaml`（主配置） | `./Aha.yaml`（工作目录） | 不存在时使用 classpath 的 `AhaDefault.yaml` |
| `Model.yml`（模型配置） | `${AHA_HOME:-~/.aha}/Model.yml` | 含 API Key，属**凭据类配置**，放用户级目录 |
| `ModelDefault.yml` | classpath（内置） | 内置供应商预设 |

**主配置查找**

```
./Aha.yaml  →  classpath:AhaDefault.yaml
```

**模型配置查找**

```
配置项 Llm.ModelFile 指定的路径
   ↓ 未指定
$AHA_HOME/Model.yml      （已设置 AHA_HOME）
   ↓ 未设置
~/.aha/Model.yml
   ↓ 不存在
classpath:ModelDefault.yml（内置预设，只读）
```

> **不读取工作目录下的 `./Model.yml`**：相对路径会随 CWD 变化，且读不到时静默回退，
> 难以排查。需要项目级配置时，在 `Aha.yaml` 中显式设置 `Llm.ModelFile`（如 `./Model.yml`）。

**合并优先级**（高 → 低）

```
命令行选项  →  ./Aha.yaml + Model.yml  →  classpath:AhaDefault.yaml / ModelDefault.yml
```

`Model.yml` 中同名供应商会覆盖 `ModelDefault.yml` 中的预设，
并以其中的 `Model.Default` 作为当前选中供应商。

### 4.2 主配置 `Aha.yaml`

顶层只有 `Aha` 一个键，其下 7 段。

#### `Aha.Agent` — Agent 身份

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `PromptFiles` | list | `[AHA.md, AGENTS.md]` | 提示词文件名，逐级向上查找取首个**非空**文件 |
| `SystemPrompt` | string | `""` | 内联系统提示词；仅当未找到任何文件时生效 |

**解析优先级**

| 优先级 | 来源 |
|---|---|
| 1 | CLI `--system` |
| 2 | CLI `--system-file` |
| 3 | 工作目录 → 项目根，逐级向上查找 `AHA.md` → `AGENTS.md`（就近优先，跳过空文件） |
| 4 | 用户级目录 `$AHA_HOME` 或 `~/.aha` 下的同名文件 |
| 5 | `Aha.Agent.SystemPrompt` |
| 6 | 内置默认身份 |

> **「不重复注入」的含义**：身份在**会话创建时**解析并固化到 `SessionConfig`，
> 同一会话内不重复读文件、不重复解析。
> 但 LLM API 无状态，**每轮请求仍需携带 system 消息**——「不重复」指不重复配置，而非不发送。

#### `Aha.Llm` — 模型

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `DefaultProvider` | string | `null` | 默认供应商键名（通常由 `Model.yml` 的 `Model.Default` 决定） |
| `Providers` | map | `{}` | 供应商明细；0.1 已分离至 `Model.yml` |
| `Fallback.Provider` | string | `OpenAI` | 兜底供应商 |
| `Fallback.Model` | string | `gpt-4o` | 兜底模型 |
| `ModelFile` | string | `${AHA_HOME:-~/.aha}/Model.yml` | 模型配置文件路径 |

**兜底触发条件**：`Model.yml` 缺失、默认供应商不存在、或调用失败。

#### `Aha.Memory` — 记忆

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `Storage` | string | `sqlite` | 存储类型 |
| `Path` | string | `${AHA_HOME:-~/.aha}/Data/Aha.db` | 数据库路径 |
| `MaxContextEntries` | int | `100` | 最大上下文条目数 |

#### `Aha.Tools` — 工具

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `Enabled` | list | 全部 5 个内置工具 | 启用的工具名 |
| `AutoApprove` | list | `[]` | 额外自动放行的权限 |
| `Shell.AllowedCommands` | list | `[]`（不限） | 允许执行的命令 |
| `Shell.TimeoutSeconds` | int | `30` | 命令超时秒数 |

**权限与放行规则**

| 权限 | 默认行为 |
|---|---|
| `READ` | 自动放行 |
| `NETWORK` | 自动放行 |
| `WRITE` | 需确认（`--yes` 或 `Tools.AutoApprove` 含 `WRITE`） |
| `EXECUTE` | 需确认 |
| `ADMIN` | 需确认 |

`AutoApprove` 可取值：`WRITE` / `EXECUTE` / `ADMIN` / `ALL`。

#### `Aha.Security` — 安全

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `KeyStore` | string | `encrypted-file` | 密钥存储类型 |
| `KeyStorePath` | string | `${AHA_HOME:-~/.aha}/Key/Aha.keystore` | 密钥库路径 |

#### `Aha.Logging` — 日志

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `Level` | string | `DEBUG` | 日志级别；内置默认 `DEBUG`（开发阶段有意保留全量），用户级可覆盖 |
| `File` | string | `${AHA_HOME:-~/.aha}/Log/AHA.log` | 日志文件 |

#### `Aha.Extension` — 扩展

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `Enabled` | bool | `true` | 扩展总开关 |
| `Path` | string | `${AHA_HOME:-~/.aha}/Extension` | 扩展目录 |
| `AutoLoad` | bool | `true` | 启动时自动加载 |
| `Disabled` | list | `[]` | 禁用的扩展 ID |
| `Settings` | map | `{}` | 各扩展设置 |

### 4.3 模型配置 `Model.yml`

```yaml
Model:
  Default: DeepSeek          # 当前选中供应商
  Providers:
    <ProviderId>:
      Adapter: openai-compatible
      BaseUrl: https://api.deepseek.com/v1
      ApiKey: "${AHA_API_KEY_DEEPSEEK}"
      Model: deepseek-chat
      TimeoutSeconds: 120
      MaxRetries: 3
      RateLimit:
        Rpm: 60
        Tpm: 100000
      Extra: {}
```

| 键 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `Adapter` | string | 是 | `openai-compatible` / `anthropic` / `gemini` |
| `BaseUrl` | string | 是 | **基础地址**，不含端点路径 |
| `ApiKey` | string | 是 | 支持 `${ENV}` / `${ENV:-default}` 占位 |
| `Model` | string | 是 | 模型名 |
| `TimeoutSeconds` | int | 否 | 超时秒数，默认 `120` |
| `MaxRetries` | int | 否 | 最大重试次数，默认 `3` |
| `RateLimit.Rpm` | int | 否 | 每分钟请求上限 |
| `RateLimit.Tpm` | int | 否 | 每分钟 Token 上限 |
| `Extra` | map | 否 | 供应商特有参数，透传 |

**内置供应商预设**

| ID | BaseUrl | 默认模型 | 适配器 |
|---|---|---|---|
| `OpenAI` | `https://api.openai.com/v1` | `gpt-4o` | `openai-compatible` |
| `Anthropic` | `https://api.anthropic.com` | `claude-sonnet-5-1` | `anthropic` |
| `Gemini` | `https://generativelanguage.googleapis.com` | `gemini-2.5-flash` | `gemini` |
| `DeepSeek` | `https://api.deepseek.com/v1` | `deepseek-chat` | `openai-compatible` |
| `BigModelCN` | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` | `openai-compatible` |
| `Qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-max` | `openai-compatible` |

> **中转站**：`sub2api` / `new-api` 等提供 OpenAI 兼容端点，
> 用 `--adapter openai-compatible` 接入即可。

### 4.4 占位符与路径展开

**环境变量占位符**（用于任意字符串字段）

| 语法 | 行为 |
|---|---|
| `${VAR}` | 取环境变量；未设置则取 JVM 系统属性；仍未设置则**保留原占位符** |
| `${VAR:-default}` | 同上，但未设置时用 `default` |

**为什么保留未解析占位符**：便于诊断——直接看到 `${AHA_HOME}` 就知道该设哪个变量，
而不是静默替换为空串导致路径变成 `/Data/Aha.db` 这类错误值。

**`~` 展开**（仅路径类字段）

| 写法 | 结果 |
|---|---|
| `~` | 用户主目录 |
| `~/x`、`~\x` | 主目录下的 `x` |

---

## 5. 环境变量

### 5.1 运行时变量

| 变量 | 作用域 | 说明 |
|---|---|---|
| `AHA_HOME` | AHA 自身 | 数据根目录。被默认配置的 `${AHA_HOME:-...}` 占位符引用 |
| `AHA_LOG_FILE` | 日志 | 日志文件路径（优先级低于 `Aha.Logging.File`，见 §4.2） |
| `AHA_LOG_LEVEL` | 日志 | 日志级别（优先级低于 `Aha.Logging.Level`） |
| `JAVA_HOME` | 启动脚本 | 指定 JDK；未设置则用 `PATH` 中的 `java` |
| `JAVA_TOOL_OPTIONS` | JVM | JVM 自动读取的额外参数（非 AHA 专有） |

`AHA_HOME` 的影响范围（默认值均以其为根）：

| 用途 | 默认路径 |
|---|---|
| 模型配置 | `$AHA_HOME/Model.yml` |
| 记忆数据库 | `$AHA_HOME/Data/Aha.db` |
| 密钥库 | `$AHA_HOME/Key/Aha.keystore` |
| 日志 | `$AHA_HOME/Log/AHA.log` |
| 扩展目录 | `$AHA_HOME/Extension` |
| 用户级提示词 | `$AHA_HOME/AHA.md`、`$AHA_HOME/AGENTS.md` |

未设置 `AHA_HOME` 时，各占位符的 fallback 不一致，需注意：

| 用途 | 未设 `AHA_HOME` 时的 fallback |
|---|---|
| `ModelFile` | `~/.aha/Model.yml` |
| `Memory.Path` / `KeyStorePath` / `Logging.File` / `Extension.Path` | `./`（**当前工作目录**） |
| 用户级提示词 | `~/.aha/` |

> 这是已知的**不一致**：模型配置走 `~/.aha`，其余走 CWD。
> 生产使用建议**显式设置 `AHA_HOME`**，使所有路径收敛到同一根目录。

### 5.2 API Key 变量

供 `Model.yml` 中的 `${VAR}` 占位符读取。命名规则由 `aha provider add` / `aha init` 生成：

```
AHA_API_KEY_<PROVIDER_ID_大写下划线化>
```

| 供应商 ID | 环境变量 |
|---|---|
| `OpenAI` | `AHA_API_KEY_OPENAI`（内置预设，与自动推导不同） |
| `Anthropic` | `AHA_API_KEY_ANTHROPIC` |
| `Gemini` | `AHA_API_KEY_GEMINI` |
| `DeepSeek` | `AHA_API_KEY_DEEPSEEK` |
| `BigModelCN` | `AHA_API_KEY_BIG_MODEL_CN` |
| `Qwen` | `AHA_API_KEY_QWEN` |
| 自定义 `<Id>` | 由 `aha provider add` 自动生成，可用 `--api-key` 覆盖 |

> **注意**：`OpenAI` 的预设变量是 `AHA_API_KEY_OPENAI`（不是自动推导得到的
> `AHA_API_KEY_OPEN_AI`）——推导按驼峰切词，会把 `AI` 拆开。
> 因此 `aha init` / `provider add` 在 API Key 为空时会**沿用配置中已有的占位符**，
> 而不是重新推导。

**设置方式**

```bash
# Linux / macOS
export AHA_API_KEY_DEEPSEEK=sk-xxxxx

# Windows CMD
set AHA_API_KEY_DEEPSEEK=sk-xxxxx

# Windows PowerShell
$env:AHA_API_KEY_DEEPSEEK="sk-xxxxx"
```

> `aha init` 交互模式会将 Key **明文写入** `Model.yml`（权限 `rw-------`）。
> 若不想落盘，改在环境变量中设置，并确保 `Model.yml` 中该字段为 `${AHA_API_KEY_...}` 占位符。

### 5.3 任意自定义变量

`Model.yml` / `Aha.yaml` 中任何字符串都可使用 `${VAR}` 占位，不限于上述变量：

```yaml
Model:
  Providers:
    Relay:
      BaseUrl: "${MY_RELAY_URL:-https://relay.example.com/v1}"
      ApiKey: "${MY_RELAY_KEY}"
```

---

## 6. 解析优先级速查

### 6.1 供应商与模型

```
--provider / --model（命令行）
   ↓
Model.yml: Model.Default → Providers.<id>
   ↓
Aha.yaml: Llm.DefaultProvider
   ↓
ModelDefault.yml（内置预设的 Default）
   ↓
Aha.yaml: Llm.Fallback.Provider / Model     ← 调用失败时的兜底
```

### 6.2 系统提示词（Agent 身份）

```
--system  →  --system-file  →  逐级向上查找 AHA.md / AGENTS.md  →  ~/.aha/  →  Aha.Agent.SystemPrompt  →  内置身份
```

### 6.3 配置值

```
命令行选项  →  ./Aha.yaml / Model.yml  →  classpath 内置默认
```

---

## 7. 相关文档

| 主题 | 文档 |
|---|---|
| 快速开始 | [GettingStarted.md](GettingStarted.md) |
| 配置详解 | [ConfigurationGuide.md](ConfigurationGuide.md) |
| 供应商设置 | [ProviderSetupGuide.md](ProviderSetupGuide.md) |
| 工具使用 | [ToolUsageGuide.md](ToolUsageGuide.md) |
| 排错 | [TroubleshootingGuide.md](TroubleshootingGuide.md) |
| 构建 | [BuildGuide.md](BuildGuide.md) |
| 扩展开发 | [ExtensionAuthoringGuide.md](ExtensionAuthoringGuide.md) |
