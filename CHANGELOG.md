# 变更日志

本文件记录 AHA 项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

> 0.1.0 是首个版本，即项目基线，因此本节只包含「新增」——
> 所有能力均以最终形态描述，不记录开发过程中的调整。

## [0.1.0] - 2026-10-06

首个可用版本：Core + CLI 可运行。

### 新增
- **构建**：`.gitignore` 补充本地工具的项目索引 `.xcodemap/` 与 `versions-maven-plugin` 的备份产物 `pom.xml.upgraded`，二者不入库

- **Windows 平台**：修复三处只在 Windows 暴露的缺陷——`--help` 在非交互场景混入 ANSI 转义序列；
  `Llm.ModelFile` 的未展开占位符被直接当作路径（`Illegal char <:>`）；项目级身份查找没有边界，
  会一路走到用户主目录把 `~/AHA.md` 当成项目级身份（临时目录位于主目录之下，故仅在 Windows 触发）
- **协作留痕**：需要人工或平台权限才能完成的事项（推送提交、分支保护设为必需检查、
  定期扫描生效验证）登记进 `TODO.md` 第 11 节并写明**验收标准**，`PLAN.md` 交叉引用；
  约定此类事项不得只写在对话里
- **修复 CI 必需检查失配**：给必需腿补的 `optional` 矩阵键会改变作业名
  （`build (windows-latest, wrapper)` → `…, false)`），分支保护的必需检查再也匹配不上，
  PR 永久停在 `Expected — Waiting for status to be reported`；改为按 `matrix.os` 判定
  可选腿，不额外增删矩阵键
- **CI 卡点组合**：`Gate.yml` 增加每周**定期扫描**（`schedule`），并新增
  `Compat.yml` 做 Maven 3.9.x 兼容性验证（固定补丁版本，不用 runner 预装 `mvn`）；
  门禁第一步显式断言 `./mvnw` 实际使用的 Maven 版本与 Wrapper 配置一致，
  发布前置为 `gate + compat`
- **检查分层**：慢检查（覆盖率门禁、完整 `clean verify`、文档检查、重复率）集中到
  新的 `Gate.yml`，只在合入 `main` / `release/**` 前、手动触发与发布前运行；
  每次 push 的 `Build.yml` 只做编译与单元测试，反馈环路显著缩短
- **重复代码率检查**：新增 PMD CPD 报告（`./mvnw pmd:cpd`）与 `bin/CheckDuplication.py`
  阈值判定（默认 2.0%，0.1.0 实测 0.40%）
- **CI 对仓库侧瞬时故障有容忍度**：新增复合 action `.github/actions/maven-run`，
  依赖解析类 Maven 调用统一经它——先清本地仓库的失败标记，失败时只在「与代码无关」的
  特征（解析不到 / 传输中断 / 远端 5xx）下重试，其余立刻失败，不给真失败乘以三倍时间
- **分支合并规范**：`ReleaseProcess.md` §4 明确长期集成分支（`dependa`）只能真合并，
  禁止「把内容重新落地一遍」；`dependa` 已用 `-s ours` 补回与 `dev` 缺失的合并关系，
  使 PR #8 从永久 `dirty` 恢复为可合并（不含任何内容改动）
- **工具失败的日志语义**：工具层的已知拒绝（未知工具 / 未启用 / 未获授权）改记 `INFO`
  且不带堆栈——它们与「用户拒绝授权」同类，属预期业务结果；Console 阈值是 ERROR，
  原先记 ERROR 会让模型偶尔叫错工具名就在终端刷出堆栈。未预期异常仍记 `ERROR` + 堆栈
- **测试输出出口**：新增 `aha-core/src/test/resources/log4j2-test.xml` 把测试日志写入
  `target/test-logs/` 并关闭 console；`ConsoleToolApproverTest` 捕获 stdout/stderr
  并顺势断言授权提示内容。构建日志里的测试输出从约 130 行降为 0
- **覆盖率门禁自证**：新增 `bin/ReportCoverage.py` 并在 `Gate.yml` 的 verify 之后执行，
  把各模块与合计覆盖率写进日志；此前 JaCoCo 的 `check` 通过时不出声，日志上与「没配门禁」
  无法区分（判定仍由 `jacoco:check` 独家执行，脚本只报数、阈值读自 `pom.xml`）
- **开发日志**：新增 `Docs/DevLog/DevLog-20261007-21.md`，记录门禁静默这一问题的核实方法
  （配置检查 + 抬阈值使其失败一次）与结论
- **开发日志目录**：新增 `Docs/DevLog/`，排障与事故按 `DevLog-YYYYmmdd-HH.md` 留痕
  （必备背景 / 排障过程与修复链 / 最终验证结果 / 关键教训 / 涉及文件清单五节）；
  首篇记录 CI 必需检查因矩阵作业名变更而永久挂起
- **CI 平台矩阵**：新增 `macos-latest` 可选腿，以 `continue-on-error` 标注，
  仅作演示与提前暴露跨平台退化，不参与必需检查、也不代表已支持 macOS（见 `BuildSpec.md` §4.1）
- **测试与 CI**：测试类隔离 `AHA_HOME` / `user.home` 并不再假定「环境里没有身份文件」；
  断言改用平台自身路径；构建矩阵改为 `fail-fast: false`，避免一条腿失败即取消其余腿而掩盖平台差异
- **文档**：选型清单改为只写主版本线（README / 设计文档 / Constitution），`BuildSpec.md` §7 新增
  「版本单一来源」规则，消除文档与父 POM 之间的依赖版本漂移

- **启动横幅**：交互式会话启动时在右侧打印 `Logo.svg` 对应的**纯 ASCII 标志**（两档尺寸，按终端宽度自动换档或隐藏），顶栏字段与标志并排、其余细节整宽在下；字段对齐改为按显示宽度补位，修正中文标签错列
- **启动横幅**：标志提供**像素风**（48x48 / 24x24 半块字符，粗像素、硬边界）与**线框风**（纯 ASCII）两种，`aha chat --logo auto|pixel|ascii|off` 切换；宽度不足时逐档降级，非 TTY 不着色不出标志
- **文档**：权威设计文档移至 `Docs/AHA/AHA-Design-V1.md`（原 `Docs/AHA-Design-V1.md`）；正文相对链接、两处目录树、附录 A 索引、7 个 SVG 占位说明与全部外部引用同步更新
- **文档**：新增 [PixelLogoDesign.md](Docs/Design/PixelLogoDesign.md)（像素风启动标志设计）与生成器 `bin/GenPixelLogo.py`（`--verify` 检测像素网格漂移，已接入 CI）
- **文档**：新增 [LoggingDesign.md](Docs/Design/LoggingDesign.md)（日志设计）——装配方式与踩坑（为何必须配置当前 `LoggerContext`）、分级分流、文件命名与切分规则、取舍与已知限制
- **文档**：设计文档新增「双向消息结构（JSON 层面）」——内部中间表示、三家请求/响应（非流式与流式）的真实报文、字段映射总表与复现步骤；示例全部使用真实模型名
- **术语统一为「扩展」**：代码注释与 CLI 帮助文案中残留的「插件」全部改为「扩展」（`aha extension --help` 此前显示「插件管理」，与命令名不一致）
- **文档命名统一**：`CliDesign.md` 改名为 `CLIDesign.md`，与 `TUIDesign.md` / `GUIDesign.md` 一致
- **身份来源各自成条 `system` 消息**：系统提示词占第一个片段，之后每个身份文件各占一个，运行环境块单独一个；不再拼成一整段，模型因此能区分内容来自哪个文件。Anthropic 改用 `system` 块数组、Gemini 改用多 `parts` 以保住片段边界
- **默认候选补入 `CLAUDE.md`**（Claude Code 兼容）：开箱即可读取项目的 `CLAUDE.md`，无需改配置
- **未列出的兼容文件不再静默忽略**：项目里存在 `GEMINI.md` / `.cursorrules` 等已知兼容文件但未列入候选时，启动信息会提示并给出加入方法
- **内容相同的身份文件只注入一次**：同一份约定被复制成 `AGENTS.md` 与 `CLAUDE.md`时不会重复注入
- **同一层级内的身份文件全部生效**：用户级 `AHA.md` 与 `AGENTS.md` 并存时都注入（此前只取第一个），层级顺序仍为「用户级 → 项目级」
- **启动信息打印身份加载顺序**：逐个列出「层级 + 路径 + 字符数」，并说明内联兜底是否使用，身份组装不再是黑箱
- **身份文件候选名与写入文件名统一由 `Agent.PromptFiles` 决定**：新增兼容文件（如 `CLAUDE.md`）只改配置即可，读路径与 `/memory` 写路径同步跟随
- **全局记忆文件自动创建**：`aha chat` 启动时若用户级 `AHA.md` 不存在则建骨架（只动用户级目录），全局记忆因此有确定落点；启动信息新增「全局记忆」一行
- **`/memory view` 分节展示全部身份文件**（全局在前）；来源不再只报优先级最高那份（多份为 `files:<用户级>;<项目级>`），避免「全局记忆看起来没生效」
- **AHA 默认系统提示词**：内置身份声明补入 `AhaDefault.yaml` 的 `Agent.SystemPrompt`（此前为空串，`/config` 里显示「（未设置）」）；`/config` 对该栏给首行 + 总字数，完整正文用 `/memory view`
- **会话内命令补全与实时着色**：`/` 开头时 Tab 弹出候选菜单（含用法说明、支持模糊匹配），命令名实时染绿（已支持）或红（未知）——不必等回车才知道拼得对不对
- **`/memory`（原 `/prompt`）**：查看与编辑 Agent 身份文件。默认写**用户级** `AHA.md`，可用 `--project` / `--file` / `--name` 指定目标；文件不存在时新建
- **用户级与项目级身份叠加生效**：此前是「首个命中即返回」，项目里存在 `AGENTS.md` 会让用户级身份整段失效
- **工具区块改为终端转录形态**：首行 `类别 + 工具名 + 程序名`（加粗、鲜艳类型色），命令行改为 `<目录><提示符> <命令全文>` 并高亮白；类别标签改 4 字（读取文件 / 执行命令 …）；
  错误正文不再回显命令原文（命令行已完整展示）
- **输入框上下边框改为高亮紫**：下边线改由状态区首行承担（JLine 的 `Status.setBorder` 无法上色）
- **标题六级各自可辨**：一级「粗+下划线+亮青」逐级递减到六级「弱化+斜体+青」；有色时去掉 `#` 标记（颜色已说明级别），无色时保留（唯一的层级信号）
- **表格单元格解析行内样式**：`` `代码` `` / `**粗体**` / `*斜体*` / 链接；列宽按解析后的宽度算；撑宽时退回纯文本以保对齐
- **Markdown 表格渲染**：缓冲整块后按显示宽度对齐输出框线表（支持列对齐、缺列补齐、超宽压缩列宽）
- **链接渲染**：`[文本]` 加 `(地址)` 显示为「文本 + 弱化地址」，不生成 OSC 8（重定向后会变乱码）
- **章节标题分级配色**：保留 `#` 标记并弱化、正文按级别区分字重与颜色
- **运行环境注入**：系统提示词在内置身份后追加操作系统、命令执行方式（`cmd.exe /c` 或 `/bin/sh -c`）、可用命令与工作目录 —— 避免模型发出 Unix 风格命令后
  在 `cmd.exe` 下失败，且报错看不出是平台不匹配

#### 模块结构

- 项目骨架：父 POM + 6 个子模块 —— `aha-common`、`aha-extension-api`、`aha-core`、
  `aha-tool`、`aha-cli`、`aha-desktop`，全部启用 JPMS
- `aha-common`：共享模型、`Tool` SPI、事件模型、异常层次；**零外部依赖**（仅 JDK）
- `aha-extension-api`：扩展契约（`AhaExtension` / `ExtensionContext` / `Registration`）、
  扩展描述符、事件总线（`EventBus` / `EventKey` / `DispatchMode`）、扩展点接口
- `aha-core`：LLM 适配体系、配置模型与加载、安全存储、记忆存储、Agent 引擎、扩展运行时
- `aha-tool`：内置工具（file / http / shell），经 `ServiceLoader` 发现
- `aha-cli`：picocli + JLine 命令行入口
- `aha-desktop`：桌面端占位模块（0.2 实现）
- Maven Wrapper（固定 Maven 4 运行时，兼容 Maven 3.9.x）
- 工具与适配器服务同时在 `module-info` 的 `provides` 与 `META-INF/services` 中声明，
  兼容 JPMS 与 classpath 两种运行方式
- `LocalAgentService` 提供可注入构造器（`LlmClient` / `ToolRegistry` / `dbPath`），
  支持测试与嵌入

#### 命名与配置约定

- **YAML 字段**用 PascalCase；**SQLite 表名（单数）与字段名**用 snake_case，
  时间字段沿用 `gmt_create`；表为 `session` / `message` / `memory`，
  索引为 `idx_message_session` / `idx_memory_session`
- **统一的用户级目录布局**：`$AHA_HOME`（默认 `~/.aha`）下按大驼峰（PascalCase）单数命名 ——
  `Data/`（数据库）、`Key/`（密钥库）、`Log/`（日志）、`Extension/`（扩展）；
  `aha init` 会创建这四个目录
- **默认路径落在用户级目录**：未设置 `AHA_HOME` 时默认 `~/.aha`，不使用当前工作目录
- `ConfigLoader.expandHome` 支持 `~` / `~/x` / `~\x` 展开
  （`Path.of` 不会展开 `~`，否则会创建出名为 `~` 的字面目录）
- **配置模板用单引号包裹占位符**：YAML 单引号不做转义处理，避免 Windows 路径
  `C:\Users\x\.aha` 中的 `\U` 被当作非法转义而启动失败；
  `ConfigTemplateTest` 扫描资源守住该约束
- 配置解析优先级：命令行 > 环境变量 > 配置文件 > 内置默认；
  占位符支持 `${ENV}` 与 `${ENV:-默认值}`，未解析时保留原样而非替换为空串

#### 模型与供应商

- 模型供应商配置与主配置分离：供应商明细集中于 `Model.yml`，
  主配置只保留兜底模型（`Llm.Fallback`）与模型文件路径（`Llm.ModelFile`）
- **`Model.yml` 默认位于用户级目录**：`$AHA_HOME/Model.yml`（未设 `AHA_HOME` 时为
  `~/.aha/Model.yml`），与 `~/.aws/credentials`、`~/.docker/config.json`、`~/.npmrc`
  同属凭据类配置的用户级惯例；需要项目级配置时在主配置中**显式**设置 `Llm.ModelFile`
- classpath 内置模型配置 `ModelDefault.yml`（6 个供应商），未创建 `Model.yml` 时生效
- `ModelConfigStore`：读写 `Model.yml`，支持 `setDefault` / `putProvider` / `removeProvider`
- CLI 一键切换与增删：`aha provider use <id>`（切换）、`add`（新增，默认保存到 `Model.yml`）、
  `remove <id> -y`（删除）
- 降级回退：默认供应商不可用时回退到 `Llm.Fallback.Provider`
- `ConfigLoader.loadModel` / `loadModelDefault` / `merge` / `resolveModelPath` / `toYaml`
- OpenAI 兼容适配器正确拼接端点路径（`BaseUrl` + `/chat/completions`），
  并兼容已含端点后缀的写法
- `aha init` / `provider add` **优先沿用配置中已有的占位符**，仅自定义供应商才按 ID 推导
- `aha config get` 支持 `Llm.ModelFile` / `Llm.Fallback.Provider` / `Llm.Fallback.Model`；
  其中 `Llm.ModelFile` 输出解析后的**绝对路径**，文件不存在时附提示

#### CLI

- **`aha init` 初始化命令**：首次使用无需手工编写配置文件。交互模式列出内置供应商供选择，
  并可选择直接写入 API Key（输入不回显，落盘后权限为 600）；非交互模式支持
  `--provider` / `--api-key` / `--no-input` / `--force`，供脚本与 CI 使用
- **无参数默认进入交互式对话**：直接执行 `aha` 等价于 `aha chat`（与 `node` / `python` /
  `claude` / `aider` 惯例一致）；非交互环境（管道 / 脚本 / CI）仍打印帮助，避免挂起等待输入
- **`chat` 启动时显示运行时信息**：供应商、模型、端点、工作目录、项目根、会话 ID。
  模型对「你是什么模型」的自述并不可靠，这一信息让用户能直接确认请求发往何处；
  端点取自适配器的 `buildUrl`，与传输层一致
- `LocalAgentService.runtime()` 在 `RuntimeDescriptor.attributes` 中暴露
  `Provider` / `Model` / `Adapter` / `Endpoint`，无需改动 `AgentService` 接口
- 项目根探测：从当前目录向上查找 `.git` / `.aha` / `Aha.yaml`
- **全部子命令支持 `--help` / `--version`**（含嵌套的 `provider add` / `config get` 等）
- CLI `tool invoke` 的 `--param`（`key=value`）与 `--yes`（显式确认）
- `provider list` 在无配置文件时给出初始化引导，并提示当前默认供应商所需的环境变量
- **命令与配置参考**：新增 [`Docs/Guide/ReferenceGuide.md`](Docs/Guide/ReferenceGuide.md)，
  集中整理 CLI 命令参数、配置项全表、环境变量与解析优先级
- **会话内命令（斜杠命令）**：`/help`、`/config`、`/context`、`/session`、`/model [名称]`、
  `/clear`、`/tool [名称]`、`/compact [保留条数]`、`/autocompact [阈值|off]`、`/exit`。
  命令名后必须是空白或行尾，因此 `/usr/bin/ls` 不会被误判；未注册的 `/xxx` 只报错并列出可用命令，
  不静默发给模型
- **终端渲染层**：内联 REPL（保留原生 scrollback，不做全屏 TUI）。探测的是**能力而非平台**：
  `TerminalCapabilities` 判定颜色深度与列宽，`OutputEncoding` 用 `Charset.newEncoder().canEncode()`
  实测控制台能否表示 `❯` `✓` `•` 等符号，不支持时降级为 ASCII（`> `、`[ok]`、`- `）。
  **不自动 `chcp`**——PowerShell 5.1 会缓存 `[Console]::OutputEncoding`，子进程改码页反而更乱
- 流式输出按**显示宽度**折行（East Asian Width，含 emoji 与代理对），不再用 `String.length()`；
  内置轻量 Markdown 渲染（标题、列表、引用、围栏代码、行内代码、粗体 / 斜体），
  且**不牺牲流式**：块级构造只依赖行首 8 字符，行内构造缓冲上限 200 字符
- 等待期显示瞬时状态行（`⠋ 等待响应… 1.2s  deepseek-chat  · 512 tok`），
  只在输出处于行首时绘制，非 TTY 或宽度未知时全为空操作
- **`/memory`：Agent 身份文件的查看与编辑**（等价于 Claude Code 的 `CLAUDE.md`）。
  此前只能看到身份来源路径、看不到正文，改文件对已有会话也不生效。
  子命令 `view`（默认，看正文）/ `files`（候选与查找顺序）/ `edit`（唤起编辑器）/ `append <文本>` /
  `reload`；改完**立即回写会话配置，下一轮即生效**。
  这组命令只由用户输入触发，模型无法自行改写自己的指令
- **终端让位（`TerminalHandover`）与外部编辑器探测（`ExternalEditor`）**：
  `/memory edit` 要把终端交给外部编辑器，而 JLine 处于 raw mode 时外部程序读不到按键，
  因此让位前 `terminal.pause()`、完成后 `resume()`。编辑器按 `-Daha.editor` → `$VISUAL`
  → `$EDITOR` → 平台默认（Windows `notepad`、macOS `open -W -t`、其余 `nano`/`vim`/`vi`）解析，
  只选阻塞式命令
- **修复 Windows 上多行命令只执行第一行**：改为写入临时批处理文件后执行 —— 此前后续行被
  静默丢弃（不报错、退出码 0，表现为「命令成功但什么都没发生」）；顺带消除嵌套 `cmd /c` 多出引号的问题
- **工具区块重排**：首行只留「类别 + 工具名」，新增「终端 / 目录 / 命令」明细与「结果」区；
  命令与输出按显示宽度**折行不截断**（仅超长命令中间省略），末行给耗时（失败同样给）
- **工具执行实时耗时**：状态行原地重绘（区块末行只能给最终值）
- **异常带堆栈**：工具异常与回合异常都打印完整堆栈，便于事后排查
- **工具失败展示完整错误**：最多 40 行、不弱化颜色；`shell-exec` 失败时附上命令原文。
  此前只有 80 字符摘要且不做输出预览，用户看不到完整命令与完整错误
- **输入行分隔线与工具区块**：输入行由上下两条分隔线框住、行首不再有 `>` 标记；
  每次工具调用展开为带背景色带的区块，配色按操作的**后果**分类（读取 / 写入 / 执行 / 网络），
  结果行按成败着色，输出只给前 3 行并标注「还有 N 行」。
  为此 `ToolResultEvent` 新增 `success` 字段（不再从 `"ERROR: "` 前缀反推），
  并修复 `aha-tool` 只声明 `provides` 而未声明 `uses` 导致 `ServiceLoader` 抛
  `ServiceConfigurationError` 的问题
- **常驻状态行**：终端底部保留一行，等待期间实时显示**转圈帧与当前阶段**（耗时与输出规模下沉到
  工具区块的末行；模型名与用量不再挤在这一行）
  （`StatusSurface` / `JLineStatusSurface`，基于 JLine `org.jline.utils.Status` 的滚动区域）。
  按**能力**门禁而非「创建成功即可用」：类型非 `dumb`、具备 `change_scroll_region`、行数 > 0
  三者同时成立才启用，否则安静退化为不显示（`StatusSurface.NONE`）——
  `Status.getStatus()` 在 dumb 终端上也会返回对象，但占不住底部区域且 `close()` 会 NPE。
  状态行走独立通道、不写输出流，终端让位前先隐藏
- **流式期间保持输入可用**：模型还在输出时仍可敲下一条消息（排队后执行），
  `Ctrl+C` 只打断本轮而不再杀掉整个会话，已生成的部分仍会落库。
  做法是主线程常驻 `readLine`、回合跑在虚拟线程上、输出经 `LineReader.printAbove`
  打到输入行上方（`AboveStream` 负责行缓冲）。此前流式与输入严格串行，期间无人读键盘
- `AgentService.streamChat` 新增带 `CancellationToken` 的重载（默认实现委托旧签名，不破坏已有实现）；
  `ChatCommand` 每轮新建令牌，取消后**不丢已生成内容**
- **修复模块路径下 Ctrl+C 杀进程**：JLine 用反射调 `sun.misc.Signal`，而 `sun.misc` 只由
  `jdk.unsupported` 导出；`aha-cli` 未声明该依赖，于是 Ctrl+C 走默认行为直接结束 JVM。
  独立探针跑在 classpath 上不会暴露这个问题，只在模块路径下出现

#### Agent 身份与会话

- **Agent 身份与系统提示词**：可在 `AHA.md` / `AGENTS.md` 中定义 Agent 的角色与行为约定，
  文件内容作为 system 消息注入。查找顺序为「当前目录→项目根逐级向上」→ 用户级 `~/.aha/`
  → `Aha.Agent.SystemPrompt` 内联 → 内置默认身份；支持 `--system` / `--system-file` 临时覆盖
- `chat` 启动信息新增「身份来源」一行，显示实际生效的提示词文件及字符数
- **会话配置持久化**：`SessionConfig`（含系统提示词与模型）写入 `session.config_json`，
  同会话内不再重复解析；进程重启后仍可通过相同 ID 恢复；`MemoryStore` 新增 `loadSession`
- **会话可恢复**：`closeSession` 结束活跃会话并释放资源，但保留持久化记录，
  同一 ID 仍可继续对话——这是会话持久化的价值所在
- **退出时输出会话 ID**：`chat` / `run` 结束（含异常路径）后打印
  `会话已保存：<id>` 与 `续接本次会话：aha chat --session <id>`；
  `chat` 另注册 shutdown hook，覆盖 Ctrl+C / kill 等强制退出
- **多轮对话保留工具调用上下文**：`message` 表含 `tool_calls`（JSON）与 `tool_call_id` 两列，
  启动时自动迁移旧库；`loadHistory` 丢弃无法完整重建的旧消息
- **流式与非流式行为一致**：`AgentEngine.stream()` 与 `run()` 同样执行
  「推理 → 工具 → 回灌 → 再推理」循环，上限为 `MAX_TOOL_ITERATIONS = 8`
- **多行粘贴安全**：启用 `BRACKETED_PASTE`；读取异常时宽幅兜底（忽略本次输入并继续）；
  `exit` / `quit` 仅在单行且无内嵌换行时生效，避免粘贴含 `exit` 的文本意外退出
- **上下文压缩**：`/compact` 把较早的对话交给模型摘要并用摘要替换那批消息
  （默认保留最近 10 条，`/compact N` 可指定），切割点不落在工具调用与其结果之间；
  `/autocompact 285k` 设定阈值后每轮自动触发，阈值存在会话配置里，`--session` 续接后仍生效。
  摘要生成失败或返回为空时**绝不改动历史**——否则用户会既丢上下文又拿不到摘要
- `MemoryStore` 新增 `replaceHistory`（压缩）与 `updateSessionConfig`（`UPDATE` 语义，
  不重写会话创建时间）
- **`loadHistory` 取最近的 N 条**：此前 SQL 为 `ORDER BY gmt_create ASC LIMIT ?`，
  会话一旦超过 `Memory.MaxContextEntries`，模型就永远只能看到最早的 100 条，
  新发生的对话再也进不了上下文
- **项目级记忆位置与项目 ID**：`~/.aha/Project/<项目ID>/Memory/`，项目 ID 由项目根绝对路径
  推导（分隔符转 `-`、Windows 盘符大写），与 Claude Code 的 `~/.claude/projects/` 规则一致；
  `/session` 展示当前项目 ID 与记忆目录
- **CLI 版本号由构建期注入**：此前硬编码在两处（`@Command(version = ...)` 与 `VersionCommand`），
  改 `pom.xml` 不会生效，发行包会报出旧版本；现以 `pom.xml` 为唯一来源，
  经资源过滤写入 `version.properties`，picocli 侧改用 `IVersionProvider` 运行时求值。
  顺带修掉 `bin/__pycache__` 等会被误提交的产物，并补齐 `.gitignore` 的大小写规则
  （`Data/` / `Key/` / `Log/` / `Model.yml` —— 小写的 `data/` 在 Linux 上匹配不到 `Data/`）

#### 权限与安全

- 权限策略体系：`PermissionPolicy`（READ/NETWORK 默认放行，WRITE/EXECUTE/ADMIN 需授权）
- `Tools.AutoApprove` 配置项，在默认策略之上追加自动放行权限
- **交互式工具授权**：`chat` 中遇到需 `WRITE`/`EXECUTE`/`ADMIN` 权限的工具时
  **暂停并询问**（`y` 允许本次 / `n` 拒绝 / `a` 本会话内始终允许该权限）；
  新增 `ToolApprover` 契约（`aha-common`）、`ConsoleToolApprover`（CLI）与
  `ToolRegistry.setApprover`
- `chat` / `run` 的 `--yes` 选项与 `tool invoke -y` 语义对齐；
  显式确认只跳过权限裁决，**不跳过** `Tools.Enabled` 过滤
- **`Security.KeyStore` 密钥库**：按配置创建 `EncryptedFileSecretStore`；
  占位符未命中环境变量时回退读取密钥库；解析发生在**使用点**（`DefaultLlmClient`）——
  `Security` 段自身是明文，配置加载必然早于密钥库就绪
- `aha secret set|get|delete|list` 密钥管理命令；主密码支持系统属性回退

#### 工具

- **工具配置落地**：`ToolSettings`（`aha-common` 的中立配置视图）+ `Tool.configure(...)` +
  `ToolRegistry.configureAll(...)`，使 `Tools.Enabled`、Shell 超时与命令白名单全部生效
- **`shell-exec` 超时与取消**：默认 30 秒超时；超时或取消时终止**整个进程树**
  （仅终止直接子进程会留下孤儿）；在虚拟线程中读取输出，避免输出超过管道缓冲时子进程阻塞
- `shell-exec` 命令白名单仅接受「单一命令 + 无 shell 元字符 + 首个子命令在名单内」——
  只判首 token 不安全：`echo hi; whoami` 的首 token 是允许的 `echo`，但 `whoami` 照样会执行

#### 日志

- **日志系统按配置程序化装配**：由 CLI 启动时应用 `Aha.Logging.Level` / `Aha.Logging.File`
  （`LoggingSetup`）；切分文件与主文件同目录、命名为 `<基名>-yyyy-MM-dd-NN.log`（超 10 MB 即切分），console 写 stderr
  —— CLI 是组合根，`aha-core` 只依赖 `slf4j-api`，不绑定具体实现
- **日志分流**：console 仅输出 `ERROR` 以免打断对话；
  用户拒绝授权等预期业务结果记为 `INFO`，完整日志仍写入文件
- **日志格式含调用点**：`%logger.%method:%line`，输出完整类名、方法名与行号
- 补齐 `INFO` 级日志点：配置来源、会话创建 / 关闭、推理开始、工具调用与结果

#### 错误处理

- **错误信息包含服务端响应体**：流式与非流式统一输出响应体（截断至 800 字符），
  便于直接看到「模型不存在」「额度不足」等具体原因，而非只有 `HTTP 422`
- `Exceptions.unwrap` / `message`（`aha-common`）解包 `CompletionException` 等包装异常，
  CLI 错误输出不带 `com.acanx...` 类名前缀

#### 用例与质量

- 测试补齐至 **302 个用例**，覆盖 `PermissionPolicyTest`、`ToolRegistryTest`、
  `SessionManagerTest`、`ProviderPresetsTest`、`DefaultLlmClientTest`（自包含 Mock HTTP）、
  `EndToEndTest`、`LocalAgentServiceTest`、`AgentEngineStreamTest`、`BuiltinToolsTest`、
  `CliCommandTest` 等
- JaCoCo 覆盖率报告与 `verify` 门禁（BUNDLE 行覆盖 ≥ 70%）
- Javadoc 构建配置（`maven-javadoc-plugin`，`doclint=none`、zh_CN）

#### 发行与发布

- 发行包（`mvnw clean package` 产出 `dist/`）：`maven-assembly-plugin` 将 `aha-cli` 自身、
  全部模块与第三方依赖收集到 `lib/`，并附带 `bin/Aha.sh` / `bin/Aha.bat` 启动脚本；
  分发方式为 JPMS 模块路径目录，与启动脚本的 `--module-path lib` 一致
- **跨平台启动脚本**：`Aha.sh` 支持 `JAVA_HOME`、校验 `lib/` 存在并给出可读错误提示；
  `Aha.bat` 为**纯 ASCII + CRLF**（CMD 按 ANSI 代码页解析批处理），提示信息用英文；
  `bin/CheckScripts.py` 在 CI 中守住该约束
- 启动脚本传递 `--enable-native-access`，消除 JDK 25 下 `sqlite-jdbc` 与 `jline`
  的受限方法警告
- `Release.yml` 发布完整的 `dist.zip`
- 父 POM 补充发布元数据：`<url>`、`<licenses>`（Apache-2.0）、`<developers>`、`<scm>`
- `mvn clean` 一并清空 `dist/` 中的构建产物（保留目录）：assembly 只覆盖同名文件，
  旧版本的依赖 jar 会残留，而 JPMS 下一个模块出现两个版本是致命错误
  （`java.lang.module.FindException: Two versions of module ...`），
  依赖升级后重新构建出的发行包会直接启不来

#### 前向兼容契约（1.0+ 实现形态）

- `Endpoint` / `RuntimeKind` / `RuntimeDescriptor`（`aha-common.runtime`，寻址与运行时描述）；
  `RuntimeKind` 预留 `WSL` / `CONTAINER` / `SSH` / `SERVERLESS` / `DAEMON`
- `TaskRequest` / `TaskResult` / `TaskSource`（`aha-core.task`，跨进程 / 跨云任务契约，
  PascalCase JSON，包装类型 + 忽略未知字段以兼容混合版本）
- `AgentRuntime`（`aha-core.runtime`，远程运行时抽象，含默认流式语义）
- `AgentServiceProvider` SPI + `AgentServiceFactory.connect(...)`（新增形态只需新增模块）
- `AgentService.submit(TaskRequest, CancellationToken)` 统一任务入口
- 设计文档 `Docs/Design/RemoteAndProtocolDesign.md`：ACP / MCP / A2A 辨析、1.0~2.0 路线、
  配置 schema 预留、跨边界安全模型、测试策略与风险

#### 文档与工程

- `AGENTS.md`、技能体系、开发规范 / 设计 / 用户指南文档骨架
- **1.0 硬门槛：AHA 自举里程碑**。发布 1.0 正式版前，项目自身的开发、测试、构建、发布
  以及后续 AHA 功能的迭代升级，必须全部由 AHA 工具链完成，不再依赖其他 Agent Harness；
  定义 L0~L4 自举层级与 A1~A6 验收标准
- 设计文档 `Docs/Design/SelfHostingDesign.md`：自举层级、反向指标、缺口与补齐路径、
  度量方法、风险与开工清单
- `ReleaseProcess.md` 新增 1.0 发布的自举附加检查（A1~A6）
- `CommitMessageSpec.md` 新增自举期 `Harness:` 标注约定（用于度量与审计，不参与 CI 门禁）
- 构建文档按「规范进 DevSpec、操作进 Guide」拆分：
  `Docs/DevSpec/BuildGuide.md` → `Docs/DevSpec/BuildSpec.md`（强制要求：工具链锁定、
  双版本验证、覆盖率门禁）+ `Docs/Guide/BuildGuide.md`（操作：命令、报告、构建期排错）；
  `TroubleshootingGuide.md` 的构建期问题归并至 BuildGuide，本文件专注运行时与配置排错
- 文档规范增加「规范与操作分离」原则，并修正后缀约定表中 `Guide.md` 跨目录歧义
- `Docs/DevSpec/` 下 4 份规范文档以 `Spec.md` 结尾：
  `TestingSpec.md`、`YamlFieldSpec.md`、`CommitMessageSpec.md`、`DocumentationSpec.md`；
  命名规范中明确禁止 `Rule.md` 结尾
- **技能符合 Agent Skills 规范**：目录与 `name` 采用 kebab-case，入口为 `SKILL.md`
  并含 `name` / `description` / `license` / `compatibility` / `metadata` frontmatter；
  资源目录拆分为 `references/`（参考文档）与 `assets/`（模板与数据），
  资源文件名用 kebab-case；主体按渐进披露组织（何时使用 → 前置条件 → 步骤 → 参考 → 常见错误）
- `bin/CheckSkills.py`：校验技能目录结构、frontmatter、命名与内链，接入 CI `docs` job；
  技能内 Java 模板另由 `build` job 编译验证
- `bin/CheckDocs.py`：文档围栏闭合与相对链接有效性检查，接入 CI `docs` job
- `bin/CheckChanged.py`：按变更路径选择要跑的检查（文档 → `CheckDocs`、技能 → `CheckSkills`、
  脚本 → `CheckScripts`，实现代码则给出该跑的 Maven 命令），避免改一段文档也付分钟级验证代价；
  `CheckScripts.py` 的目录遍历改为单次 `os.walk` 剪枝，在慢文件系统上从 25 秒降到亚秒
- **依赖自动升级**：`.github/dependabot.yml` 每日检测 Maven 与 GitHub Actions 依赖，
  PR 先合入 `dependa` 分支再人工合并；minor 与 patch 分组，major 单独成单

[0.1.0]: https://github.com/ACANX/AHA/releases/tag/v0.1.0
