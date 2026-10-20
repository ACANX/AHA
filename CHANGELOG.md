# 变更日志

本文件记录 AHA 项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

> 0.1.0 是首个版本，即项目基线，因此**该段落**只包含「新增」——
> 所有能力均以最终形态描述，不记录开发过程中的调整。

## [Unreleased]

### 变更
- **SPI 命名统一为 ApiType（issue #361）**：
  - `LlmProviderAdapter` 新增 `default apiType()`（与配置 `Model.Providers.<Id>.ApiType` 同名），
    `providerId()` 标记 `@Deprecated`（至少保留一个 minor 版本）；
  - 三个内置适配器的 `PROVIDER_ID` 改为 `API_TYPE`（旧常量保留为 deprecated 别名）；
  - `LlmAdapterRegistry` 新增 `apiTypes()`，`find(String apiType)` 参数名对齐；
    `providerIds()` deprecated 委托；
  - `ProviderConfig.adapter()` 标记 `@Deprecated`，活代码统一走 `apiType()`；
  - 日志诊断标签区分「适配器 / ApiType」与「供应商（`Providers.<Id>`）」；
  - **迁移**：自定义适配器只实现 `providerId()` 仍可直接工作（`apiType()` 默认委托它）；
    新代码请实现/调用 `apiType()`。旧名在阶段 3（下个大版本）移除，届时属破坏性变更。

### 新增
- **中栏显示当前会话工作目录与 git 分支（2026-10-19）**：
  - 输入框下方显示当前会话 `workspace` 的完整路径；
  - 目录或其父目录存在 `.git` 时，在路径右侧追加当前分支名；
  - 直接读 `.git/HEAD`（支持 worktree 的 `.git` 文件），不启动 git 进程；
  - 纯聊天会话不显示这一行。
- **菜单项去掉省略号（2026-10-19）**：
  - 「配置」下的供应商 / 记忆 / 扩展 / 设置、工具菜单的日志、帮助里的检查更新不再自带 `…`，
    避免看起来像被截断；
  - 菜单宽度由 JavaFX 按最长项自适应，不再出现「供应商...」。
- **工作空间搜索框（2026-10-19）**：
  - 「工作空间」面板在目录列表上方新增搜索框（`aha.workspace.search`）；
  - 按目录名或完整路径过滤，输入即筛选；
  - 与「会话」搜索框样式一致。
- **会话行高与左栏快捷视图（2026-10-19）**：
  - 会话行高由 24px 调到 30px，cell 补 2/6 内边距与 13px 字号，列表高度按 `条数 × 30 + 4` 重算，标题不再重叠；
  - 左栏窄条在折叠按钮下新增两个图标快捷按钮（💬 会话 / 📁 工作空间）：展开左栏并只显示对应面板，再点一次恢复全部。
- **会话列表高度随内容伸缩（2026-10-19）**：
  - 去掉写死的 `prefHeight=200` / `minHeight=120`；
  - 行高固定 24px，列表高度按 `条目数 × 24 + 4` 计算，内容少时不出现滚动条；
  - 超出左栏可用高度时由 `VBox` 压缩列表，内部才出现滚动条。
- **顶部「配置」菜单（2026-10-19）**：
  - 菜单栏在「编辑」与「会话」之间新增「配置」；
  - 子项：供应商、记忆、扩展、设置；
  - 「日志…」放到顶部「工具」菜单，与「工具列表…」并列；
  - 左栏的供应商 / 工具 / 记忆 / 扩展 / 日志 / 设置按钮移除，统一从顶部菜单打开。
- **会话列表与工作空间去重、可折叠（2026-10-19）**：
  - 已归属工作空间的会话不再重复出现在「会话」列表；
  - 「会话」整块改为 `TitledPane`（与「工作空间」同一种控件），可折叠 / 展开；
  - 会话列表标题只显示标题，不再拼「N 条 · 刚刚」；
  - 当前会话的标题、条数与时间移到中栏顶部（`aha.conversation.header`）。
- **工作空间列表紧凑排版（2026-10-19）**：
  - 组间距由 4px 降到 2px，组内行距降到 1px；
  - 组头上下内边距 1px，会话链接 12px 字号、1px 行内边距；
  - 展开多个工作空间时能一屏看到更多会话。
- **工作空间交互修正（2026-10-19）**：
  - 组内「＋」缩小为 18×18 / 10px，明显小于组头；
  - 新建会话改在**首条消息落库后**刷新列表：之前 `ensureSession()` 一创建就刷新，
    `listSessions()` 的空会话清理会把刚建、还没消息的会话当垃圾删掉，导致会话不出现；
  - `deleteEmptySessions` 增加「保留活跃会话」参数，内存中的会话不再被误删；
  - 会话出现在列表时已带 `workspace`，归入对应工作空间。
  - 「＋ 添加工作空间」移到已有工作空间之后，字号与内边距小于组头；
  - 修复组内「＋」新建会话丢失工作空间：`pendingWorkspace` 保留到首条消息落库，
    之前 `newSession(workspace)` 在 `finally` 里清空，导致会话被创建成「纯聊天」。
- **工作空间可折叠，并支持一个目录下多个会话（2026-10-19）**：
  - 每个工作空间组头像目录一样可点击折叠 / 展开（`▸` / `▾`）；
  - 组内「＋」新建会话后，首条消息落库时自动刷新列表，新会话立即出现在该工作空间下；
  - 同一个工作空间可创建任意多个会话。
- **桌面端对话气泡左右分栏（2026-10-19）**：
  - 用户输入靠右显示，助手 / 系统 / 错误靠左显示；
  - 每条消息带圆角气泡与内边距，最大宽度 720px；
  - 「复制全部消息」仍按原顺序取文本。

### 修复
- **POSIX 发行包不再带 Windows 专属脚本（issue #381，2026-10-20）**：
  - `ubuntu-amd64` / `macos-aarch64` 的四种包（CLI-JVM / 桌面 JVM / 桌面原生 / CLI 原生）
    不再包含 `AhaCli.bat` / `AhaDesktop.bat` / `apply-update.cmd` / `AhaApplyUpdate.ps1`；
  - 新增 `.github/Python/PrunePlatformScripts.py`：按平台裁剪（POSIX 删 `.bat` / `.cmd` / `.ps1`，
    Windows 原样保留），各出包线在**生成 `AHA-PACKAGE.json` 之前**调用，清单与包内容一致；
  - `CliReleasePackageLeg` / `DesktopPackageLeg` / `BuildMvnArtifactLeg` 的入口断言改为按平台
    （POSIX 不再要求 `.bat`，Windows 仍要求）；
  - `bin/CheckScripts.py` 新增 `check_platform_package_scripts()`（各出包线必须调用裁剪脚本 +
    `--check-only` 断言），并把 `check_update_helper_packaging()` 的口径改为「Windows 包必带 helper」；
  - `ReleaseProcess.md` §3.6 与两份装配描述符的注释同步为「按平台分发」，不再留
    「非 Windows 包里带 .cmd/.ps1 属于暂时用不上」的相反说法。
- **CLI：超长 CJK 行触发 JLine WCWidth 越界，渲染崩溃被误判为回合失败（issue #384，2026-10-20 真机）**：
  - JLine 4.4.6 的 `WCWidth.charCountForGraphemeClusterBreakIterator` 会用越界 offset 调 JDK 的
    grapheme `BreakIterator`（`IllegalArgumentException: offset is out of bounds: 76`），
    经 `Display.update → Status.redraw → printAbove` 冒到 `ChatCommand#runTurn`，
    整个回合被判「执行失败」且已输出内容丢失；
  - 新增 `SafeAbovePrinter`：首次渲染异常记一条 WARN 并**永久降级为直写**
    （暂停状态区 → 写终端 → 恢复），后续行不再重试 `printAbove`，回合不再中断；
  - `runTurn` 的失败输出收敛：完整堆栈进日志，终端只给摘要 + 「完整堆栈见日志」，
    不再同屏打两份堆栈；
  - 新增 `SafeAbovePrinterTest`：正常路径、降级后不重试、suspend/resume 顺序、
    连降级都失败时不抛出、含 CJK 的长行经 `AboveStream` 不中断。
- **Linux 缺中文字体时界面汉显方块（issue #382，2026-10-20 真机）**：
  - 最小化 Ubuntu / WSL 常不预装中文字体（`fc-list :lang=zh` 为 0），JavaFX 默认族
    `System` → DejaVu Sans 不含汉字字形，合成回退也借不到字体，界面汉字全是方块；
  - 新增 `FontFallback`：Linux 上按 `Noto Sans CJK SC` → `Source Han Sans SC` →
    `WenQuanYi Micro Hei` → `Droid Sans Fallback` → 微软雅黑 / 苹方的顺序选第一个已安装族，
    写进根节点 `-fx-font-family` 作为全局界面字体；精确候选全不命中时用族名特征兜底
    （`Noto Serif CJK` 等）；Windows / macOS 不替换字体（系统自带中文，避免外观回归）；
  - 日志面板 / 工具输出 / 参数区的等宽字体链在 `monospace` 之前补上 CJK 候选；
  - 启动日志新增 `渲染诊断：中文字体 …`（选中族名 / 命中数量 / 缺失）；
    Linux 上一个 CJK 族都没有时，消息区给出含 `sudo apt install fonts-noto-cjk` 的提示；
  - 新增 `FontFallbackTest` 覆盖候选顺序、大小写不敏感、缺失降级、非 Linux 分支与等宽链位置。
- **原生包启动即崩：`config.ModelDefinition` 未登记反射元数据（issue #383，2026-10-20 真机）**：
  - `0.1.5.00380` 的 ubuntu 原生包（桌面 / CLI）启动即抛
    `UnsupportedFeatureError: Record components not available for record class
    com.acanx.module.aha.core.config.ModelDefinition`：桌面端窗口从未出现，CLI 连 `--version` 都退出码 1
    （崩溃在 `ConfigLoader.loadDefault` 这条任何子命令都会走的启动路径上）；
  - 补登记 `core.config` 下的 `ModelDefinition` / `ModelTier` / `SystemPromptLoader$ResolvedPrompt`
    到桌面与 CLI 两份 `reachability-metadata.json`；
  - 守卫由只扫 `core.update` 扩为按 `JACKSON_PACKAGES` 逐包全量扫描（`core.update` + `core.config`），
    并写明判定口径：新增 Jackson 反序列化入口时把包加进清单，其余包不登记避免噪音；
  - 原生管线补启动冒烟：桌面 Linux 腿用 `xvfb-run` 断言命中 `[ui-fxml-loaded]`，
    CLI 腿的 `--version` / `--help` 冒烟失败改用 `::error::`（仍不挡合并，但不再淡化成 warning）。
- **工作空间只放项目会话，并支持添加目录（2026-10-19）**：
  - 「纯聊天」不再出现在工作空间面板，只保留在主会话列表；
  - 工作空间面板顶部新增「＋ 添加工作空间」，通过目录选择器添加项目目录；
  - 已添加目录持久化到 `desktop.properties` 的 `Ui.Workspaces`，重启后仍在；
  - 无会话的已添加目录也会显示，可直接在其下新建会话。
- **桌面端删除会话后立刻重建空会话（2026-10-19）**：
  - `AhaDesktopApp.onSessionDelete` 不再调用 `controller.newSession()`；
  - 仅当删除的是当前会话时才清空对话区，后续发送才懒创建新会话。
- **桌面端「检查更新」查不到快照包（2026-10-19）**：
  - `ChannelContract.tag(ALPHA, ...)` 的目标词由小写 `desktop-jvm` 改为大写驼峰 `Desktop-JVM`，
    与 `SnapshotJVM` 工作流实际产出的 tag 一致；
  - `versionOfTag(ALPHA, ...)` 同时接受大写（`-AHA-Desktop-JVM`）与小写（`-AHA-desktop-jvm`）两种写法；
  - 修复 `V0.1.5.00380.10200208-AHA-Desktop-JVM` 在桌面端 Alpha 通道查不到的问题。

### 新增
- **会话记录工作空间（issue #379 阶段 1）**：
  - `SessionSummary` 新增 `workspace`（保留兼容构造器），纯聊天会话为 `null`；
  - `SqliteMemoryStore.listSessions` 从 `session.config_json` 解析 `workspace`，不新增表列；
  - `LocalAgentService.createSession` 未显式指定时自动补当前项目根（`SystemPromptLoader.findProjectRoot`）；
  - 旧会话保持无 `workspace`，界面归入「纯聊天」。
- **桌面端「工作空间」手风琴（issue #379 阶段 2）**：
  - 左栏「会话」与「供应商」之间新增「工作空间」折叠面板（默认折叠）；
  - 展开后按项目根分组会话，当前项目优先，「纯聊天」单列一组；
  - 组内「＋」在对应工作目录下新建会话，点击会话直接打开并回放历史；
  - `ChatController.newSession(workspace)` 支持把工作目录写入会话配置。
- **桌面端工作空间会话状态（issue #379 阶段 3）**：
  - 组内会话显示「● 进行中 / ○ 已结束」；
  - 点击已结束会话即打开并回放历史，可直接继续输入；
  - 提示补上「可直接继续输入」。
- **清理无内容会话（2026-10-19）**：
  - 桌面端「新建会话」改为**懒创建**：只关闭旧会话，首次发送时才落库，不再每次开窗/新建都产生空会话；
  - `MemoryStore.deleteEmptySessions()` / `SessionManager.cleanupEmptySessions()`；
  - `LocalAgentService.listSessions()` 返回前自动清理没有消息的会话，历史遗留的空条目随之消失。
- **桌面端左栏可拖拽调宽 + 会话列表滚动条减半（2026-10-19）**：
  - 左栏与中栏之间新增 5px 拖拽条，宽度限制 160–480px，折叠时自动隐藏；
  - 会话列表纵向滚动条宽度由默认约 14px 降到 7px（暗色 / 亮色主题同步）；
  - `ShellLayout` 新增 `LEFT_MIN_WIDTH` / `LEFT_MAX_WIDTH` / `SPLITTER_WIDTH` / `clampLeftWidth`；
  - `FoldState` 支持 `setExpandedWidth`，拖拽后折叠再展开仍保持新宽度。
  - 移除「记忆」「扩展」导航按钮及其提示中的「（0.2 后续）」字样，按正式功能入口展示。

## [0.1.5] - 待发布

### 变更
- **快照触发标签改名（2026-10-19）**：
  - `SNAPSHOT:Desktop` → `SNAPSHOT:JVMDesktop`，`SNAPSHOT:Cli` → `SNAPSHOT:JVMCli`
    （与原生线的 `SNAPSHOT:NativeDesktop` / `SNAPSHOT:NativeCli` 命名对称）；
  - 同步：清单 `SnapshotPaths.yml`、判定 `SnapshotPlan.py`（含 31 条自测）、守卫 `CheckScripts.py`、
    快照工作流注释、三份指南与 `Dist/README.commands.md` 副本；
  - 旧标签已在 GitHub 上**直接重命名**（历史 PR 上的标记自动跟随新名，仓库里不留同名旧标签）；
    仓库内的历史记录（Troubleshooting / DevLog / 本文件的历史段落）保留旧名，守卫按路径豁免；
  - `bin/CheckScripts.py` 新增历史豁免：旧名只允许出现在历史载体，活代码与指南仍必须用新名。
- **dev 线 JVM 预发行按形态拆分（issue #347）**：
  - 由一条 `V<版本>-AHA-JVM` 改为两条：`V<版本>-AHA-Desktop-JVM`（tag `-aha-desktop-jvm`）与
    `V<版本>-AHA-Cli-JVM`（tag `-aha-cli-jvm`），与原生线「每个目标一条」对齐；
  - 每条 release 只装本形态的包，`SHA256SUMS` 也只覆盖自己那几行；
  - 旧合并 tag `-aha-jvm` 不再新发，但保留解析与取件兼容（历史 release 仍可下载）；
  - `Dist/UpdateJvm.ps1` 按种类选择新后缀并回退旧后缀，`bin/CheckScripts.py` 的契约守卫同步升级。
- **模型配置格式重构（issue #357）**：
  - `Models` 由「档位 → 模型名」改为「**模型 ID → 模型定义**」，支持 `Name`（实际模型名）、
    `Reasoning`、`Input`（文本 / 图片）、`ContextWindow`、`MaxTokens`、`ThinkingLevelMap`（思考强度映射）；
  - 档位独立为 `Tier` 节点；默认档固定 `Standard`，`Standard` 缺失时按全局 `DefaultTier` →
    档位强弱顺序兜底；
  - 供应商级 `DefaultTier` / `Model` 不再写入；旧格式（字符串 `Models`、`Adapter` 键、
    旧 `Model`、`anthropic` 枚举值）读取时自动迁移，首次写盘即新格式；
  - `Adapter` 改名 `ApiType`，枚举 `anthropic` 统一为 `anthropic-messages`（与 pi agent 对齐，
    `--adapter` 保留为 CLI 兼容别名）；
  - 新增 `Example/.aha/Model.yml` 参考示例；`ModelDefault.yml`、`ConfigurationGuide.md`、
    `ReferenceGuide.md` 同步新格式。

### 新增
- **编辑 / 写入文件的差异展示（issue #323）**：
  - `file-write` 调用展示旧内容与新内容的行级 diff；
  - `+` / `-` 前缀在无颜色环境也能区分新增 / 删除；
  - 只保留改动行前后 3 行上下文；无改动时显示「无改动」。
- **浏览文件工具调用的块头信息（issue #325）**：
  - 读取类块头补目标路径与行号区间（支持 `offset/limit`、`start/end`、`startLine/endLine`）；
  - 块体继续展示文件内容，超过 500 行折叠（#322）；
  - 单测覆盖全文件读取、指定区间读取与超长文件折叠。
- **工具块折叠阈值统一为 500 行（issue #322）**：
  - 单个块（成功 / 失败正文）超过 500 行才折叠，499 / 500 行完整显示；
  - 折叠提示改为「已折叠 N 行（共 M 行）」；
  - 折叠只影响终端显示，回灌给模型的原始输出不变；
  - 补 499 / 500 / 501 边界与回灌不变单测。
- **工具块改为青色底并补内边距（issue #365）**：
  - 块底色由暗灰改为深青（256：`48;5;23`；16 色：`46;30`）；
  - 块首、块尾各留一行纯底色空行，形成上下内边距；
  - 块内每行首列留空（左内边距 1 列），正文从第 2 列开始；
  - 折行、续行缩进与省略提示随内边距调整，块内所有行显示宽度一致。
- **状态区三行布局（issue #371）**：
  - 动态参数（转圈 / 阶段 / 耗时）移入第一条横线，不再与静态参数挤在同一行；
  - 倒数第二行暂以**会话 ID** 占位（后续指标待设计）；
  - 静态参数独占最后一行；
  - 受 JLine 限制，输入行上方的可重绘行仍无法实现（见 #358）。
- **围栏代码行号与语言识别增强（issue #371）**：
  - 有色 TTY 下代码块每行前置 3 位行号（`  1 │ `），无色 / 非 TTY 保持原样；
  - 围栏语言标识支持 ` ```java title="x" ` 等附加信息（只取第一个 token）；
  - 行号不改变行数，代码内容仍完整。
- **BigModelCN 补充最新 GLM 模型**：
  - 新增 `glm-5.4`（旗舰推理模型），`Tier` 的 Ultra / Pro / Standard 指向它；
  - 保留 `glm-5.3` / `glm-5.3-flashx` / `glm-5.3-flash` 作为 Flash / Fallback；
  - 桌面端预设与候选模型同步。
- **围栏代码语法高亮（issue #336）**：
  - 支持 Java / Python / JavaScript / TypeScript / JSON / YAML / Shell / SQL / XML /
    Go / Rust / C# / C / C++ / PHP / Ruby；
  - 单行内区分关键字 / 字符串 / 注释 / 数字，不改变行数与换行；
  - 未知语言或未标语言回退纯文本；无色终端不产生 ANSI。
- **工具调用分块展示：块内同底色 + 块间间隙（issue #326）**：
  - 工具块的首行、命令行、明细、正文、末行统一使用同一暗灰底，整行铺满，块内不再出现深浅断层；
  - 失败块同样使用中性底 + 红色前景，避免「块头灰底、结果红底」把一块拆成两块；
  - 连续两个工具块之间保留空行，边界一眼可辨；
  - 新增 `Style.blockLine(...)`，并同步 `ToolBlock.command / details / body` 与 `StreamRenderer`。
- **CLI 运行状态位（issue #342）**：
  - 状态行八态：空闲 / 请求中 / 生成中 / 工具执行中 / 等待审批 / 完成 / 失败 / 已中断；
  - 请求中与工具执行中显示实时耗时（120ms 刷新）；
  - 完成 / 失败保留 2 秒后回到空闲；失败只显示单行原因；
  - ESC / Ctrl+C 中断显示「已中断」，保留 2 秒后回到空闲；
  - 审批问答前后由 `ConsoleToolApprover` 回调切换「等待审批」。
- **中断状态独立展示（issue #359）**：
  - 新增 `INTERRUPTED` 运行状态：ESC / Ctrl+C 后显示 `⏹ 已中断`（不显示 `✗` 与失败原因），
    保留 2s 后回空闲；
  - 审批问答被中断（Ctrl+C / EOF / 无输入）同样落到该状态，不再误切回「执行工具」；
  - `ConsoleToolApprover` 新增 `onCancelled` 回调；单测覆盖中断文案与回调。
- **CLI 底部状态栏：静态字段（issue #330 / #353）**：
  - 空闲时在输入框下方常驻显示「运行模式 · 供应商 · 档位 · 模型 · 思考强度 · 目录 (分支)」，
    不随输出滚动消失；忙碌时阶段在前、字段在后（宽度不足先保阶段）；
  - 供应商取运行时 `Provider`；思考强度暂无数据源时显示 `-`（不编造数值）；
  - 目录是 Git 仓库时展示当前分支（`feature/x` 完整保留，detached 显示 7 位短 sha）；
  - 目录超过 3 层截断为 `…/末三层`；宽度不足时依次丢目录+分支、强度、档位，
    模式与供应商始终保留；
  - 新增 `--strict`（严格模式：含只读在内所有工具调用都需确认），与 `--yes` 互斥；
    会话内输入 `a` 后状态栏即时切到「完全授权」。
- **Markdown 富文本渲染基线补齐（issue #335）**：
  - 行内**删除线**（`~~文本~~`，SGR 9）—— 单个 `~` 仍是字面量，不影响 `~/x`；
  - **嵌套列表**保留缩进层级，符号与顶层统一（不再出现顶层 `• `、嵌套 `- ` 两套记号）；
  - 单测补删除线、嵌套列表、粗体 + 行内代码混合、非 TTY 无 SGR 四类路径。
- **运行时 `ESC` 中断当前对话任务（issue #341）**：
  - 生成中按 `ESC` 停止流式输出，工具执行中按 `ESC` 终止子进程（含后代进程，不留孤儿）；
  - 中断只影响当前任务，会话上下文与已生成正文保留，随后可直接输入下一条；
  - 中断时丢弃尚未累积完整的工具调用，避免历史里留下半截 `tool_calls`；
  - 斜杠候选面板展开时第一下 `ESC` 收起面板、第二下中断任务；
  - 非交互式终端（管道 / CI）启动时明确提示「ESC 中断不可用」，不静默失灵。

### 修复
- **表格后紧跟的块级构造失效（CLI Markdown 渲染）**：
  - 表格内的换行由表格模式自行消费、不经过行结束处理，`prefixDone` 停在 `true`，导致**紧跟表格的第一行**丢掉行首判定：
    `### 标题`、`- 列表`、`> 引用`、```` ``` ```` 围栏全被当正文原样吐出（第二行起恢复正常）；
  - 表格结算时统一复位行级状态（`kind` / `headingLevel` / `prefix` / `prefixDone`），并补 `lastEmitted` 保证 `_` 的词内判定正确；
  - 单测新增表格后紧邻标题 / 列表 / 引用 / 围栏四类相邻组合用例。
- **发布链路加固（issue #303，V0.1.4 发布事故复盘）**：
  - 原生报告包只在主包产出后才上传，不再出现「有 report、没有主包」的孤立资产；
  - 发布资产不齐时自动把 release 降为 **pre-release**，资产齐全后自动恢复（不完整版本不再被当成正式版推荐）；
  - 原生腿的真实结果与「形态 × 平台」缺失矩阵写进 Job Summary，不再被 `continue-on-error` 隐藏；
  - 新增 `bin/CheckReleaseAssets.py`：发布前按矩阵预演、发布后验收 12 份齐全；
  - GraalVM 版本取值四级兜底（`IMPLEMENTOR_VERSION` → `native-image --version` → 路径名 → 报错），修复 Linux / Windows / macOS 三平台原生腿；
- **旧资产名别名移除**：`check_release_matrix()` 的 0.1.5 版本门槛生效，旧前缀与旧段序不再被接受。

## [0.1.4] - 2026-10-18

### 变更
- **发布物矩阵与命名统一（issue #269）**：
  - 资产名统一为 `aha-<product>-<form>-<版本>-<os>-<arch>-<runtime>.zip`，四种形态 × 三平台共 12 份：
    `aha-desktop-jvm-*` / `aha-desktop-native-*` / `aha-cli-jvm-*` / `aha-cli-native-*`；
  - **CLI 从「平台无关一份包」改为三平台各自构建**（`windows-amd64` / `ubuntu-amd64` / `macos-aarch64`），名字与内容一一对应；
  - 矩阵单一来源：`.github/ReleaseMatrix.yml`（守卫 `check_release_matrix()` 钉住工作流 ↔ 矩阵 ↔ 文档一致）；
  - 快照 tag 去掉 `-SNAPSHOT` 后缀（5 段版本已能区分），读取侧兼容旧后缀；
  - 原生报告包改名为 `-report.zip`（原 `-diagnostics.zip` / `-Report.zip`，读取侧兼容旧名）；
  - 原生包的 `AHA-PACKAGE.json` 新增 `toolchain.graalvm`（精确 GraalVM 版本）；
  - 旧资产名保留为兼容别名，**0.1.5 起由守卫版本门槛判红**。

## [0.1.3] - 2026-10-17

### 新增
- **桌面端应用内升级（issue #51，2026-10-16）**：设置面板里多一个「升级」tab，可选通道、检查更新、看版本列表、升级 / 降级；下载 → 校验 → 由外部 helper 换装 → 自动重启。
  - **三通道**：`Stable`（正式发版，当前还没有带资产的正式发版）/ `Beta`（dev 最新合并，**默认**）/ `Alpha`（PR 试包，7 天有效期，首次切换需确认）；
  - **契约只有一处**：通道 ↔ tag ↔ 资产名定义在 `ReleaseProcess.md` §3.5，PowerShell / Python / Java 三处实现逐字一致由守卫钉住；
  - **不静默**：缺本平台资产时明确报错并列出该版本**实际资产**（不退到更旧版本）；「没有校验和」与「校验和不符」分开表达，后者**不落地**；降级要二次确认并提示用户数据不回滚；
  - **换装由外部 helper 完成**（正在运行的进程换不掉自己的文件）：App 写计划 → 拉起 helper → 主动退出，helper 等它退出后备份 → 替换 → 逐文件按清单哈希自证 → 重新拉起 → 写回执；失败自动回滚并仍尝试拉起旧版本；
  - 命令行兜底入口：`AhaDesktop.bat --update-to <版本> [--yes] [--check]`；
  - **平台范围**：本期只实现并只验证 Windows；Linux / macOS 仍可检查更新，升级按钮置灰并给手动取件指引；
  - 设计与验证记录：[Docs/Design/DesktopAutoUpdateDesign.md](Docs/Design/DesktopAutoUpdateDesign.md)；用法：[Docs/Guide/AppUpdateGuide.md](Docs/Guide/AppUpdateGuide.md)。
- **发布资产的两份契约文件（issue #51 §6，2026-10-16）**：四种包形态的每个 zip 现在都在**包根**带一份 [`AHA-PACKAGE.json`](Docs/DevSpec/ReleaseProcess.md)（`kind` / `version` / `platform` / `jdk` + 每个文件的 `path` / `sha256` / `size`），每个 release 都带 `SHA256SUMS`（每个 zip 一行）。
  - **为什么**：应用内升级要靠它知道「哪些文件属于这个包」「新包删掉了哪些旧文件」，并在解压后**逐文件**核对大小与哈希；发布页校验和则覆盖「下载是否被截断 / 传输是否被篡改」。
  - **只有一份实现**：`.github/Python/PackageManifest.py`（四条出包线都调它，避免各写一段 shell 后漂移 —— 漂移的表现是「某个通道的包永远校验不过」，静默且难查）；
  - **硬闸口**：打包垃圾（`__MACOSX/`、`.DS_Store`）与非法路径（绝对路径、`..`、反斜杠）一律失败，不写进清单；缺清单的包在原生线上**不让发布**；
  - `Release.yml` 的校验和由**单独的收尾作业**生成：两个并列作业各自直传资产，各写一份会互相覆盖，得到「只列了一半资产」的校验和；
  - **守卫**：`bin/CheckScripts.py` 新增 `check_release_channel_contract()`，把「通道 ↔ tag ↔ 资产名」的契约钉在 **PowerShell / Python / Java 三处逐字一致**（正向 + 两次反向注入验证）。
- **部署前验证：非阻塞质量验证工作流（issue #166，2026-10-16）**：把「**部署前应当自测、但还不宜直接进合并卡点**」的
  通用验证 / 断言集中到一个独立工作流 [`.github/workflows/PreDeployVerify.yml`](.github/workflows/PreDeployVerify.yml)，
  **每次 PR 推送自动跑**（`opened` / `synchronize` / `reopened`），也可手动触发（`workflow_dispatch`）。
  - **留痕**：commit status **`predeploy-verify`** + 幂等 PR 评论（验证对象 / 结论 / 执行人 / 时间 / 对应提交）+ Job Summary；
  - **非阻塞**：**不进**必需检查 ⇒ 缺它 / 失败**都不拦合并**；成熟后把它加进 required 即成为卡点，**机制不改**；
  - **失败不静默**：失败也打 `failure` status、也留痕评论（`if: always()`），作业同时变红；
  - **0 token**：验证项全在本地进程里跑，不做任何模型往返；
  - **验证项**（跑在**真实构建产物**上，定义在 `bin/PreDeployVerify.py` 的 `CHECKS`——**加一项 = 加一个函数**）：
    V1 产物能起 / V2 `--help` 列得出全部子命令 / V3 `tool list` / V4 `tool invoke file-read` **真的读到文件** /
    V5 `provider list` + `model show` / V6 全程无 `UnsatisfiedLinkError`；
  - **守卫**：`bin/CheckScripts.py` 新增 `check_pre_deploy_verify_contract()`，断言**触发（三事件 + 手动）/ 触发层不过滤 / 权限 / 失败也留痕（`if: always()`）/ status 名与规范一致 / 脚本自检已接线**；
  - **与 §2.2 的分工**：`/snapshot-assert` 承载「机器验不了、必须人看」的项（快照包真机观感），本工作流承载「机器能验、只是还不敢卡点」的项。
- **CLI 控制台编码自动协商（issue #235，2026-10-16）**：Windows 控制台按代码页解释字节，`1252` / `936` 下中文会变成 `?`、像素风启动标志退化为纯 ASCII 线框。现改为：程序启动时（任何输出之前）经 FFM 调 `SetConsoleOutputCP(65001)` / `SetConsoleCP(65001)`，并重建 `System.out` / `System.err` 为 UTF-8。**仅当标准输出真的连着控制台**（`GetConsoleMode` + `System.console()`）才改，**管道 / 重定向 / CI 一律不动**，退出时恢复原代码页；失败静默回退到原有的「探测 + 提示 + 降级」。新增 `tty/WindowsConsole.java`、`tty/ConsoleEncoding.java`，`tty/OutputEncoding.java` 与 `AhaCli` 接入。
- **PR 快照包（pre-release 试包）全线上线（2026-10-15）**：给**未合并**的 PR 出可下载的试包，把自测提前到合并之前。
  - **出包**：4 个触发标签 `SNAPSHOT:Desktop` / `SNAPSHOT:Cli` / `SNAPSHOT:NativeDesktop` / `SNAPSHOT:NativeCli`（**粘性**：标签在则每次 push 重出，移除即停）+ `/snapshot <目标> [平台…]` 评论命令（一次性、大小写不敏感、仅写权限账号生效）+ `workflow_dispatch`；`OS:Win` / `OS:MacOS` / `OS:Ubuntu` 收窄到单平台（不能单独使用）；只改 `Docs/**` 的 PR 不出包，除非**新打**触发标签（显式请求）。
  - **载体与命名**：**每个目标一条** pre-release，tag = `V<基线>.<PR 补零 5 位>.<北京时间戳>-AHA-<目标>-SNAPSHOT`；目标词 `Desktop-JVM` / `Cli-JVM` / `Desktop-Native` / `Cli-Native`（**与资产前缀同序**）；`Release.yml` 增加负向过滤 `!V*-SNAPSHOT` / `!v*-SNAPSHOT`，避免快照触发正式发版。
  - **本地取件**：`Dist/UpdateDesktopNativeSnapshot.cmd` / `Dist/UpdateCliNativeSnapshot.cmd`（原生）与 `Dist/UpdateSnapshot.ps1 -Kind jvm`（JVM）——零配置（不需要 token / `gh` / Python）、无参数 = 最近一次、状态独立记在 `Dist/.aha-snapshot.json`；原生桌面端取件会把 exe **替换**到启动器同目录（旧版留 `.bak`、下载前探占用），`-NoInstall` 可只取件。
  - **自动清理**：`SnapshotCleanup.yml` 在 PR 关闭 / 合并后**异步**回收（另有每日兜底与手动触发）；同一 PR 同一目标保留最近 3 条、有效期 7 天（按发布页 `created_at`）；安全阀只删严格匹配的快照 tag，正式发版与 dev 线 tag 不碰。
  - **守卫**：`.github/snapshot-paths.yml` 是触发标签与 A/B/C 路径清单的**单一来源**；`SnapshotPlan.py` / `SnapshotCleanup.py` / `SnapshotComment.py` 均可本地 `--self-test`；`SnapshotComment.py` 只对瞬时 API 错误做有界重试。
- **一键更新原生包（2026-10-13，issue #156 / TD-00015）**：
  ① `Dist/UpdateDesktopNative.cmd` / `Dist/UpdateCliNative.cmd`——Windows 双击即把最新原生包
  取到 `Dist/`（依次探测 `py -3` / `python` / `python3`，找不到时给出下载指引）；
  ② **CLI 原生包首次有自动取件能力**（`Script/Python/CliNativeVersionUpdate.py`），与桌面端
  **同构**且不写第二份逻辑：引擎抽成 `Script/Python/NativeVersionUpdate.py`，两种包只在
  `KINDS` 表里差五项（tag 后缀 / 资产前缀 / 可执行文件名 / 状态文件名 / 帮助里展示的用法），
  新增第三种原生包 = 表里加一行；历史命令名 `DesktopNativeVersionUpdate.py` **保持可用**
  （现为薄壳）。实测：本机取到 `V0.1.3.00154-aha-cli-native`，`version` 输出 `AHA 0.1.3.00154`。
  ③ **网络层两条路（2026-10-13 追加）**：Windows 上若本机 Python 的 HTTPS **硬失败**
  （`OPENSSL_Uplink ... no OPENSSL_Applink`，进程被直接终结、进程内 `try/except` 捕不到），
  `auto` 会用**子进程**探一次并自动改用系统 `curl.exe`（系统 TLS）；也可用
  `--downloader auto|urllib|curl` 显式指定。详见 `TS-2610-036`。
  ④ **Windows 一键入口改为 PowerShell 原生实现（2026-10-13 晚）**：真机双击报
  `no OPENSSL_Applink`（本机 Python 的 OpenSSL 硬失败，进程被直接终结、进程内捕不到）⇒
  `Dist/UpdateNative.ps1` 用 `Invoke-RestMethod` / `Invoke-WebRequest` 走 .NET Schannel TLS，
  **完全不经过 Python**，也**不需要用户装 Python**（与姊妹项目 LegionFanControl 同一做法）；
  Python 路径（POSIX）保留并带 `--downloader` 兜底。两套实现的命名契约由 `bin/CheckScripts.py`
  断言逐字一致；同时补上 `.ps1` 的 UTF-8 BOM 规则、批处理「不得用括号转义」规则，
  并修正「`Dist/` 被整体跳过、里面两个 `.cmd` 从未被检查」的守卫缺口。
  Windows 侧已在 PowerShell 7.6.7 下**实跑**验证（安装 / 幂等 / `.bak` / 状态文件 / 两个错误分支），
  `Build.yml` 新增 `native-update-script` 作业把这一跑搬进 CI（真机复测前先在 CI 拦住同类坑）。
  背景见 `TS-2610-036`。
  整体另见 TD-00015 与 `Docs/Design/DesktopNativeUpdateDesign.md`。

### 文档
- **新增《GitHub Actions 规范》（2026-10-16）**：新增 [`Docs/DevSpec/GitHubActionsSpec.md`](Docs/DevSpec/GitHubActionsSpec.md)——约束工作流文件的**写法**（命名 / 触发 / 权限 / 结论与留痕 / 守卫），与 `BuildSpec.md`（门禁分层）/ `ReleaseProcess.md`（发布链路）分工；同时把工作流**命名**统一为**大驼峰**（`Snapshot Assert` → `SnapshotAssert`、`PreDeploy Verify` → `PreDeployVerify`；文件名 `snapshot-cleanup.yml` → `SnapshotCleanup.yml`），并接入 `bin/CheckScripts.py::check_workflow_names()`（文件名 / `name:` / 两者一一对应三条一起断言）。
- **GraalVM / GitHub Actions 参考文档（2026-10-15，issue #28）**：新增 [`Docs/Guide/GraalVMInActionsGuide.md`](Docs/Guide/GraalVMInActionsGuide.md)——六层可配置面、Action 输入速查（**逐项抄自 `action.yml`**）、发行版与版本语法、native-image 参数定法与「四类清单」闭环、【**权威查询入口**】含 `native-image --help-extra` 等本地自证命令与逐个实测过的官方链接、症状→参数对照表、本仓库落地位置索引；`BuildGuide.md` / 两条原生设计文档 / 原生技能 `SKILL.md` / `AGENTS.md` 同步交叉引用。
- **快照包文档补齐（2026-10-15）**：`BuildSpec.md` 新增 §11「快照产物（PR 试包）」（载体与命名硬约定、路径清单单一来源、生命周期与清理安全阀、与门禁的关系）；`BuildGuide.md` §3.3 补全原生线并更正取件口径；`ReleaseProcess.md` 新增 §2.1（快照与正式发布的隔离）；`CommandCheatsheet.md` §7 同步更正；新增 TS 记录 `[TS-2610-039](Docs/Troubleshooting/TS-2610-039-Artifact-Anonymous-Download-401.md)`。
- **原生镜像取件工具：实现方案定稿与文档对齐（2026-10-14，issue #156）**：
  `Docs/Design/DesktopNativeUpdateDesign.md` 改为权威口径（v1.2.0）——标题与定位不再限桌面端，
  新增§0.1「两套实现，一份契约」、§0.2「Windows 侧验证记录」、§0.3「演变要闻」；
  `DesktopNativeDesign.md` / `BuildGuide.md` / `NativeRenderDiagnosticsGuide.md` /
  `AHA-Design-V1.md` 同步为「Windows 双击 `.cmd`（无需 Python）+ POSIX Python」。
- **Troubleshooting 32 篇档案补齐四节必备项（2026-10-13，issue #128，选项 C）**：
  按姊妹项目 `TroubleShootingDocSpec.md`，排查档案有**四节不得缺**——
  「结论分层 / 观测数据 / 排除项 / 未决问题」。本仓 32 篇（`TS-2610-001`~`032`）此前
  **一篇都没有**（四节覆盖度实测 **0/32**）。本轮：
  ① 32 篇逐篇以**文末补入块**补齐四节（形式统一、不带章序号，避免与各档案既有编号冲突）；
  ② `TS-Template.md` 同步补上（新档案照着模板写，模板不合规会复制缺陷）；
  ③ `DocumentationSpec.md` **新增 §5「TS 档案正文骨架」**（v1.23.0），写清写法与
  「不回溯改写 12 节骨架」的取舍；
  ④ `bin/CheckDocs.py` 新增 **`[TS 档案必备章节检查]`**——写入规范而无人执行等于没写，
  这条现在是机器强制的（临时改掉某篇的「排除项」标题，检查如期失败并点名）。

  补入内容**全部来自档案原文**（数字、命令、日志、结论、遗留项逐条搬运并分类），
  **没有新增任何未经记录的事实**；档案本身就没保留的，**如实写成缺口**
  （「原档案未保留原始输出」「本机无该环境」「当时未验证」）——
  这正是四节里「未定位」与「未决问题」的意义：把**没做完的部分显式留痕**，而不是被读成「已解决」。

  两处口径订正：篇数是 **32** 而非 31（命名规范化后又新增了 `TS-2610-032`）；
  规范落点是 **§5** 而非原以为的 §4（§4 已是「更新制度」，且被 `AGENTS.md` 与
  `IssueTrackingSpec.md` 引用，不得重编号）。

### 变更
- **Agent 执行边界落地：本地「没有构建能力」从约定变成执行域约束（issue #70，2026-10-17）**：
  `bin/AgentSandbox.sh` 用 Linux 命名空间（bubblewrap）把命令放进受限执行域——域内 `/mnt`（Windows 盘）
  不可见、宿主 JDK 目录逐个盖成空目录、`mvn` / `mvnw` / `java` / `javac` / `gradle` 全是拒绝桩
  （命中即退出码 `97` 并提示「去看 PR 的 CI checks」）；**工作区文件一个都不改**，git 看不到虚假变更。
  - 启动器修正与实际增强：`--repo` 由**占位改为真生效**（`bwrap --chdir`）、新增 `--print-plan` 取证、
    仓库位于 `/mnt` 时直接拒绝（含 `/mnt` 自身——这条是自证脚本抓出来的）、JDK 隐藏改为
    「静态清单 + 从 `java` 真实路径反推」并加「必须真含 `bin/java`、不许盖过浅目录」两条护栏；
    三份 JDK 清单先 `readlink -f` **归一 / 去重 / 只留最浅根**——同一个 JDK 常有互为符号链接的多条路径
    （CI 上 `/usr/lib/jvm/temurin-25-jdk-amd64` → `/opt/hostedtoolcache/.../x64`），对同一目录 tmpfs 两次
    会让整个域起不来（这条是自证**在 CI 上首次运行时**抓出来的，本机复现不了）；
  - 新增自证 `bin/CheckAgentBoundary.py`（20 条断言）：域内构建入口全部被拦、`/mnt` 不可见、
    工作区不被改写、`git` / `gh` / `python3` 仍可用；**反向用例**保证「隔离失效时它会变红」；
    非 Linux / 无 `bwrap` 时明确**跳过**（退出码 3）而不是判绿；
  - 接入 `Gate.yml`（装 `bubblewrap` + 跑自证；Ubuntu 24.04 runner 需放开 AppArmor 对非特权
    user namespace 的限制，已在步骤里说明并处理）；
  - 变异验证（在 `/tmp` 副本上做）：分别拆掉 `/mnt` 隐藏、桩 PATH、`JAVA_HOME` 的 java 桩、
    整个 bwrap，自证分别报出 2 / 3 / 2 / 6 条失败——不是「怎么写都绿」；
  - 人类开发者**不受影响**（`./mvnw` 照常）；Windows 侧原生启动的工具（P2）与 shell 自动进域
    （Q1，属仓库外机器配置）**未做**，见 issue #70 的分阶段表。
- **应用内升级的默认通道改回 `Stable`（issue #252 / TD-00019，2026-10-16）**：此前默认 `Beta` 是
  被现实逼出来的临时值（正式 release `0.1.0` / `V0.1.1` / `V0.1.2` 的资产数均为 0，默认 `Stable`
  会让升级面板首屏就是空列表）。改回之后，「**从没选过通道**」的安装会跟着默认值走，**显式选过**
  `Beta` / `Alpha` 的不受影响（`Update.Channel` 缺键不写盘）。
  - **形态边界没变**：`Stable` 目前只发 JVM 形态的包（原生线的正式发布排在 1.x）⇒ 原生形态
    （`AHA-Desktop-Native` / `AHA-Cli-Native`）在 `Stable` 通道列不出可升级版本，升级仍走 `Beta` / `Alpha`；
  - **修掉一个让「跟随默认值」失效的坑**：升级面板渲染初始选中会触发通道切换监听器，把
    `Update.Channel` 记成「用户显式设过」——只要打开过一次升级面板，该安装就再也不跟随代码默认值。
    现在「设成当前有效值」不算显式选择，渲染初始选中也不再触发保存（`UpgradePane.applyingInitialChannel`）。
  - 顺手把两处提示改成**可行动**：通道列不出内容时说「也可以切到「…」或「…」通道再看看」，
    「本通道没有这种包」按通道契约**动态列出**可用通道（原来只写「见 issue #51 §2.1」）。
- **自动打的 `V*` tag 现在会自动触发发布（issue #238，2026-10-16）**：此前 `Build.yml` 用
  `GITHUB_TOKEN` 推 tag，因 GitHub 防递归**不会**触发下游工作流，于是 Release 页只有 tag、
  没有发行包，只能由人删 tag 重推。现改用该规则的**官方例外** `workflow_dispatch`：`Build.yml`
  的 tag 作业在**新建**出 tag 后用 `gh workflow run Release.yml --ref main -f tag=<TAG>` 派发发布
  流程（权限补 `actions: write`），**不配置 PAT**。`Release.yml` 侧新增 `workflow_dispatch` 入参、
  两条出包线显式 `checkout` 发布 tag、`action-gh-release` 显式 `tag_name`；`--ref main` 保证
  `GITHUB_SHA` = `main` head = tag 指向的提交，`gate` / `compat` 与两条出包线都落在同一提交上。
  幂等口径不变：tag 已存在时**跳过且不派发**；手工重建 tag 降为兜底。
- **快照自测留痕：`snapshot-assert`（默认启用、默认不卡点；issue #166）**：PR 里发一条评论即可把
  「这一版快照我按清单跑过了」记成 commit status，留痕（版本 / 平台 / JDK / 验证人 / 时间 / 对应 sha）落在 PR 评论里。
  - 命令：`/snapshot-assert <版本> <平台> [jdk27] [+model]`（打 `success`）、
    `/snapshot-assert-fail <版本> <平台> [jdk27] <原因>`（打 `failure`——失败也要可见）；平台词与快照出包同一套。
    该前缀以 `/snapshot` 开头 ⇒ 另外三条命令式工作流的守卫里加了一条排除，避免它们对这条命令回「命令格式应为 …」。
  - **默认不卡点**：`snapshot-assert` **不**进 required status checks，缺它不影响合并；要卡点由 ACANX 在分支保护里显式加入。
  - **必测项一律 0 token**：清单（A1–A5 / B1–B4 / C1–C3）不含任何模型往返 —— 工具直调（`tool invoke`，绕过模型）
    + 配置 / 本地命令 + 开窗观感即可覆盖「机器验不了」的那部分；需要真实 provider 请求的动作降级为**可选**（`+model`），
    因为适配器没有本地 mock、任何真实对话都要花 token，而 token 由 owner 的 key 承担。
  - 安全与失效：只认有写权限的账号；status 绑 PR 当时的 `head_sha`，PR 再 push 后旧验证自动失效；快照 tag 必须属于本 PR。
  - **使用指南**：`Docs/Guide/SnapshotSelfTestGuide.md`（含端到端示例与常见拒绝处置）。
  - 配套守卫：`bin/CheckScripts.py::check_snapshot_assert_contract()`（context / 触发守卫里的命令 / 三条工作流的排除 / `statuses: write` /
    清单编号两处一致 / 必测行 token 必须为 0），清单原文见 `ReleaseProcess.md` §2.2。
- **打包形态的静态守卫 + dev 线包体自证收紧（2026-10-15，issue #218 / #217）**：
  `bin/CheckScripts.py` 新增 `check_package_layout()`（装配描述符不得再有 `dependencySet`、
  `fileSet` 不得输出到 `bin/` 或 `lib/`、单体 jar 落地名不得带版本号、启动脚本不得回到
  `--module-path`）与 `check_shade_attached()`（shade 必须显式 `shadedArtifactAttached=true`
  + `shadedClassifierName=standalone`）—— 这四条各自对应一次真出过的问题（`lib/` 里同一份 jar
  存两遍、fat jar 顶替主产物），而症状都是**静默**的（包照出、体积翻倍、某条链路拿到错包）。
  同时把 `Release.yml` 的平台严格断言移植到 dev 线：桌面端包改为**按平台点名**本平台原生库
  （`linux`⇒`libglass.so`、`win`⇒`glass.dll`、`mac-aarch64`⇒`libglass.dylib`）并禁止混进别的
  平台的库（原来只判「原生库 > 0」）；CLI 补「任意深度的原生库 > 0」（CLI 无 JavaFX，
  `sqlite-jdbc` / `jline` 的原生库在 `org/**` 下）。
- **CLI JVM 便携包改为「包根平铺」，并修掉包内同一份 jar 存两遍**（2026-10-15，issue #218 / #217）：
  `aha-cli-<版本>.zip` 里不再有 `lib/` 与 `bin/` —— 单体 jar 落到**包根**并**去掉版本号**
  （固定叫 `aha-cli.jar`），启动脚本 **`AhaCli.sh` / `AhaCli.bat`** 也从 `bin/` 移到包根；
  解压后直接 `AhaCli.bat`（或 `java -jar aha-cli.jar`）就能跑，两条线（CLI / 桌面端）形态一致。
  启动脚本同时**改名对齐桌面端**：jar 叫 `aha-<模块>.jar` 时脚本叫 `Aha<模块>.{bat,sh}` ——
  `aha-desktop.jar` ↔ `AhaDesktop.bat`，于是 `aha-cli.jar` ↔ **`AhaCli.bat` / `AhaCli.sh`**
  （原 `Aha.bat` / `Aha.sh`）。
  - **顺带修掉「里外重复」**：shade 出来的单体 jar 原先会**顶替项目主产物**，于是装配描述符的
    `dependencySet`（`useProjectArtifact=true`）把它当「项目工件」又收进 `lib/` 一份 ——
    包里同一个类存两遍，55MB（issue #217）；而且 `aha-cli-native` 那条线声明了对 `aha-cli`
    的依赖，拿到的也是这个单体 jar，再叠上它自己拷的 runtime 依赖，classpath 上人人两份。
    现在 shade 改为**附带产物**（`shadedArtifactAttached=true` + classifier `standalone`），
    主产物保持普通 jar：装配描述符按 classifier 取单体 jar 铺成 `aha-cli.jar`，
    原生镜像线拿到的仍是「模块 jar + 依赖 jar」。
  - 启动脚本：源码检出处会在本目录、上一级与 `Dist/` 找 `aha-cli.jar`；都没有时自动解压
    `Dist/aha-cli-*.zip`，**旧形态的包（`bin/` + `lib/`）会被点名拒绝**并给出处置办法
    （不必再靠「找不到主类」之类的二次报错去猜）。
  - 这条也把 issue #217 收掉：`lib/` 不再产生，自然不存在「fat jar 覆盖模块化 jar」。

- **JVM 便携包取件：新增「平铺到落地根」，并修掉「静默退回旧版本」（2026-10-15，issue #218）**：
  `Dist\UpdateDesktopJvm.cmd` / `Dist\UpdateCliJvm.cmd` 取件后，把该版解压到
  `Dist\desktop-jvm\<版本>\`（**暂存**），再把**去版本号**的入口**复制**到 `Dist\` 根
  （桌面端 `aha-desktop.jar` / `AhaDesktop.bat` / `AhaDesktop.sh`，CLI 侧为 `aha-cli.jar`）——
  于是命令**与版本无关**：`Dist\AhaDesktop.bat`、`java -jar Dist\aha-desktop.jar` 直接可用，
  换版本不必改任何一处；`-NoInstall` 可只解压不做平铺。
  - **同时修掉一个会误导人的行为**：目标资产缺失时脚本原先**静默退回更旧的 release**
    （实测：最新版缺 Windows/jdk25 那条腿的资产，于是退到 `00216` 那个 `bin/` + `lib/` 的旧包，
    看起来就像「新版本不支持 fat jar」）。现在**只看最新一版**，缺资产即报错并列出该版实际有哪些资产；
    真要退旧版必须显式说出口（`-AllowOlder`，或 `-Tag` 指定版本）。
  - **暂存目录用完即清**：平铺成功后把 `Dist\desktop-jvm\` / `Dist\cli-jvm\` 删掉 ——
    否则同一个包在 `Dist/` 里存两遍（桌面端 25 MB、CLI 55 MB）；想翻包内容加 `-KeepExtract`。
    快照取件同理：原生快照把 exe 装进落地根后，那份解压目录也会清掉（exe 已就位、zip 留着、
    旧版有 `.bak`）；JVM 快照没有可安装的 exe，解压目录本身就是要跑的东西，**保留**。
- **桌面端 JVM 便携包改为「包根平铺」形态（2026-10-15，issue #218）**：包内不再有 `lib/` 与 `bin/` ——
  包根只有 `aha-desktop.jar`（单体 jar：本项目模块 + 全部运行时依赖 + **本平台** OpenJFX 的类与
  原生库，原生库在运行期由 JavaFX 自己从 jar 取出并缓存）、`AhaDesktop.sh` / `AhaDesktop.bat` 与
  三个文档。jar 名**去版本号**，启动脚本与用户命令因此与版本无关（换版本不改任何一处）。
  单体 jar 的入口是新增的 `AhaDesktopLauncher`——它**不继承** `javafx.application.Application`，
  只把 `main` 转发给 `AhaDesktopApp.main`；主类一旦继承 `Application`，JVM 启动器在 `java -jar`
  下会直接报「JavaFX runtime components are missing」。
  - 配套：`Script/Python/DesktopDistBuild.py` / `DesktopDistExtract.py` 的自证与落地判据同步改为
    平铺形态（并清掉上一版 `lib/` + `bin/` 的遗留文件，CLI 的 `bin/Aha.*` 与 `aha-cli-*.jar` 不动）；
    `bin/AhaDesktop.*` 改为定位 `aha-desktop.jar`（脚本同目录或上一级），源码检出处仍自动解压
    `Dist/aha-desktop-*.zip`；工作流的桌面端自证改为「包根平铺 + 单体 jar 内必含 JavaFX 类与
    本平台原生库 + 不得含 `module-info.class`」。
  - 顺带把 upstream 的修法固化进规范：用 shade 一律 `shadedArtifactAttached=true` +
    `shadedClassifierName`，**不得**让 fat jar 顶替项目主产物（issue #217 的实测教训；
    原生镜像线依赖 `aha-desktop` 的模块化 jar）。
- **主题色收敛为「一处手写色源」（2026-10-14，issue #153）**：两份主题补丁表从「手抄字面色号」
  变成**生成物**——`bin/GenThemeCss.py` 把 `aha-desktop/src/theme/*.template.css` 里的
  `%角色%` 代入 `Palette.java` 的色表；`bin/CheckTheme.py` 重新生成一遍并**逐字节比对**，
  另加一条「两份模板的角色序列必须完全一致」（同一控件在两主题下必须用同一个**角色**上色）。
  顺带把四道静态门禁（`CheckDocs` / `CheckSkills` / `CheckScripts` / `CheckTheme`）挂进
  `Build.yml` 新增的 `static-guards` 作业——它们此前只在 `Gate.yml` 里跑，而 Gate
  **不覆盖对 `dev` 的 PR**，于是「写入规范而无人执行等于没写」在 dev 分支上重演。
  **纯搬运**：两份表各 **58** 处色号生成前后**逐值一致**（与 `git HEAD` 版本去注释后比对），
  观感零变化。为什么不照最初的 A 方案改用 CSS 变量：issue #49 第四轮已证伪
  （原生镜像下自定义 color 查表失败会丢掉整条声明），两份 CSS 的头部注释就写着这条结论。
  详见 `TS-2610-035` 与 `Docs/DevLog/20261014-09.md`。

- **补齐界面路径的自动化覆盖（2026-10-13，issue #144）**：FXML 化四批期间反复暴露同一个缺口——
  静态对账只管「fxml 自身写对没有」、原生冒烟只管「界面在原生包里装起来没有」，而
  **装起来之后的交互**在 CI 上从未执行过。两项改动：

  1. `Build.yml` 新增**可选作业** `ui-smoke`（Linux + Xvfb）真开窗口跑 `AhaDesktopSmokeTest`
     ——那套断言（三栏折叠三条路、Enter 发送、工具卡片、会话列表）其实早就写好了，
     只是被 `@EnabledIfSystemProperty("aha.ui.tests")` 关着。按 `BuildSpec` §7，
     它是 `continue-on-error` 的：**不进必需检查、不挡合并**，失败以红叉 + Job Summary 暴露。
  2. `DesktopNative.yml` 增加**带路径过滤**的 `pull_request` 触发（只碰 `aha-desktop/**`、
     `aha-desktop-native/**`、`pom.xml`、该工作流自身时才跑）：让「原生包能不能起」
     **在合并前**可见。**PR 事件下不发布**——否则会在尚未合并时往发布页推预发行包、
     留下无人认领的 tag；制品仍走 Actions artifact。

  见 `Docs/DevLog/20261013-16.md`；规范同步到 `BuildSpec.md` §8.1（v1.29.0）、
  `DesktopNativeDesign.md` §4.1，`TS-2610-032` §6 的「仍存在的盲区」标注为已补齐。
- **界面静态资源按类型分目录（2026-10-13）**：`aha-desktop` 的静态资源由「与 Java 类同包的平面
  目录」改为 `view/fxml/`（7 个骨架）/ `view/css/`（2 份主题表）/ `view/image/`（logo.png）。
  **Java 类与包名不变**，因此 `module-info` 的 `opens`、native-image 的资源白名单（按扩展名匹配）
  与打包描述符**都不用改**。同时新增 `view/UiResources.java` 把 10 条路径集中到一处
  （原先散在 7 个控制器 + `ThemePaint` + `LogoImage`），以后调目录只改它；
  新增 `UiResourcesTest`：**真的去 classpath 取一次**每条路径——这是白名单对账拦不住的那一半
  （它按扩展名匹配，路径写错也照样命中）。见 `Docs/DevLog/20261013-15.md`。
- **其余四个对话框迁到 FXML（2026-10-13，批次 4，最后一批）**：`SettingsDialog`（设置）/ 
  `ApprovalDialog`（授权）/ `OutputDialog`（输出）/ `ToolListDialog`（工具列表）共 521 行、
  33 处手写控件迁到各自的 `*-dialog.fxml`，四个类改作 FXML 控制器。决定按钮（复制 / 关闭 /
  拒绝 / 本次允许 / 本会话始终允许）是 `Dialog` 自己的 `ButtonType`，不进 fxml。
  `FxmlViewChecks` 再添两条：**fxml 必须是良构 XML**（JDK 解析器，无需 JavaFX 工具包）
  与**扫元素前剔除 XML 注释**（修掉注释里 `List<T>` 造成的假阳性）。
  元数据新增 4 条（控制器字段注入）。见 `Docs/DevLog/20261013-14.md`。
- **日志面板迁到 FXML（2026-10-13，批次 3）**：`LogPanel`（240 行 / 9 处手写控件）的结构迁到
  `log-panel.fxml`，本类改作 FXML 控制器；工具栏（级别过滤 / 刷新 / 复制 / 清空 / 统计行）与
  只读日志区在 fxml 里一眼可见。「关闭」仍是 `Dialog` 自己的 `ButtonType`，不进 fxml。
  本批是第一次真正复用批次 2 抽出的 `FxmlViewChecks`——并把「节点 id 必须来自公开常量」
  也搬进助手，主窗口与日志面板共用。元数据新增 1 条（`LogPanel` 控制器字段注入）。
  见 `Docs/DevLog/20261013-13.md`。
- **供应商对话框迁到 FXML（2026-10-13，批次 2）**：`ProviderDialog`（617 行 / 61 处手写控件）
  的界面结构迁到 `provider-dialog.fxml`，两个 `GridPane` 的全部行由 `GridPane.rowIndex`
  定位；本类改作 FXML 控制器（`setController(this)`），接线收敛到
  `wireView` / `wireEditGrid` / `wireCreateGrid` / `wireKeyRow`。
  档位（`ModelTier` 的 5 档）写进 fxml 以保证表单可读，并用**两道守卫**防「枚举改了、
  fxml 忘了改」：控制器里的数量硬校验 + `ProviderDialogFxmlTest` 的三向静态对账。
  同时把批次 1 写的 5 条 FXML 对账抽成可复用的 `FxmlViewChecks`（批次 3 / 4 直接复用）。
  元数据新增 7 条（`ProviderDialog` 控制器字段、`ChoiceBox` / `ComboBox` / `MenuButton` /
  `PasswordField` / `Separator` / `GridPane`）。见 `Docs/DevLog/20261013-12.md`。
- **桌面端界面骨架迁到 FXML（2026-10-13）**：`DesktopShell` 的界面骨架（菜单栏 / 三栏 /
  状态栏 / 输入区，原约 840 行控件构造代码）迁到 `aha-desktop` 资源目录下的
  `desktop-shell.fxml`，`DesktopShell` 改作它的 **FXML 控制器**——公开方法与行为不变，
  冒烟测试按 id 的 `lookup` 全部照旧。新增 `javafx-fxml` 依赖（父 POM 的
  `dependencyManagement` 一并登记）。原生镜像侧：`.fxml` 并入 `-H:IncludeResources`
  扩展名白名单（**刻意不用**宽匹配路径规则，否则会把 `Trampoline.class` 当资源编进镜像、
  触发运行期 `defineClass` 而启动即崩），并按「**按声明类登记**」的规律补齐反射元数据
  （`FXMLLoader` / `FXML` / 各控件与布局 / StAX 实现，并把 `Node` / `Parent` / `Region` /
  `Pane` / `Control` / `Labeled` 升级为 `allDeclared*`）。自证：`NativeImageMetadataTest`
  新增 FXML 用例，新增 `DesktopShellFxmlTest`（fx:id ↔ 字段 / id ↔ 常量 / 资源白名单
  三向对账，不启 JavaFX）；启动时打印 ASCII 正面标记 `[ui-fxml-loaded]` /
  `[ui-fxml-load-failed]`（带 flush）。参考姊妹项目 `TS-2610-003`（FXML 重构后
  原生包启动即崩的两层踩坑）。见 `Docs/DevLog/20261013-11.md`。
- **依赖与构建插件版本参数统一收敛（2026-10-13）**：第三方依赖与构建插件的版本一律在根 `pom.xml`
  的 `<properties>` 声明、经 `<dependencyManagement>` / `<pluginManagement>` 下发，插件块与子模块
  不再出现版本字面量；命名统一为「依赖 `<名>.version` / 插件 `<插件名>.plugin.version`」。
  补齐此前硬编码的 3 个插件版本（`compiler` / `surefire` / `jar`），`jacoco.version` →
  `jacoco.plugin.version`，删除未使用的 `testfx.version`（`D-04` 已定案不引入 TestFX），
  并在 `dependencyManagement` 补登记 `picocli-codegen`。规范见 `BuildSpec.md` §7。
  见 `Docs/DevLog/20261013-10.md`。
- **原生桌面端文本渲染临时缓解（issue #44 / #48）**：真机像素对照确认原生包**丢失了 LCD 次像素抗锯齿**
  （退化为灰度，笔画发虚、偏细；彩色边缘像素为 JVM 的 1/18.4，暗像素反而多 67%）。
  新增 `NativeRenderDefaults`，在**原生镜像内**默认写入 `prism.lcdtext=false`（改走灰度）；
  JVM 版不受影响。用户可随时回退：`-Dprism.lcdtext=…` 直接指定渲染属性、
  或用 `-Daha.desktop.nativeTextGreyscale=false` 关掉本默认值；命令行与日志诊断
  （`渲染诊断：原生文本灰度默认 …`）已同步。
  **这是临时措施、不是根治**——成因指向 DirectWrite 渲染参数在 closed-world 下读不到
  （与 #60 同源），上游修复或补上参数读取后应删除本类与调用点。
  见 `Docs/DevLog/20261013-08.md` 与 `TS-2610-030-Native-Font-Rendering-Diff.md` §6。
- **发行形态确定为两种（2026-10-09 决策）**：只保留「**JVM JAR 聚合包**」（`aha-cli-<版本>.zip` /
  `aha-desktop-<版本>-<系统>-<架构>.zip`，解压后脚本启动）与「**原生镜像二进制**」
  （`aha-cli-native` / `aha-desktop-native` 的 `native-image` 单文件）；**`jpackage` 安装包
  跳过、不采用**。
  理由：① 避免引入第三种发行形态及其安装器 / 签名 / 公证维护面；② 「免装 JDK」已由原生镜像覆盖，
  且更彻底；③ 现有两种形态可沿用同一套产物名自证与完整性自证链路。
  口径见 `ReleaseProcess.md` §3.3；受影响的文档已同步（`BuildSpec.md` / `AHA-Design-V1.md` /
  `DesktopDesign.md` / `DesktopNativeDesign.md` / `TD-00007` / `TD-00009`）。
- **排查档案（TS）命名规范收敛（2026-10-13）**：`Docs/Troubleshooting/` 下 31 篇档案由
  `TS-yyyyMM-大驼峰标题.md` 改为 **`TS-yyMM-NNN-英文词序.md`**（年月 + 三位顺序号 + 逐词连字符标题，
  对齐姊妹项目 LegionFanControl 的 `TroubleShootingDocSpec.md`）；序号按原索引登记顺序重编
  （`001`~`031`），全仓引用、各档案 H1 编号、`bin/CheckDocs.py` 命名正则与
  `DocumentationSpec.md` §1 / `Docs/Troubleshooting/README.md` §1 同步。
  正文骨架的对齐另开 issue [#128](https://github.com/ACANX/AHA/issues/128)。
  见 `Docs/DevLog/20261013-09.md`。

### 修复
- **发版流水线三处加固（2026-10-17，发版前审计）**：① 打好的每个 zip 在生成 `AHA-PACKAGE.json` 后
  **立即 `verify` 回读** —— 清单若生成有误，在发布前就红，而不是等用户下载后、「应用内升级」时撞
  「校验和不符」；② `checksums` 作业新增**资产齐全自证**（期望 CLI + 三平台桌面端共 4 份 zip，
  缺任何一份即失败并在 Job Summary 点名）—— 原来「只出了部分平台」也会绿着把发布页生成出来；
  ③ `bin/CheckScripts.py` 新增守卫 `check_release_auto_dispatch()`，把 #238 的自动派发链路
  （`workflow_dispatch` + `tag` 入参 + 显式 `tag_name` + `contents/actions: write` + 幂等 + `--ref main`）
  钉死：这条链路一旦被改坏，表现是「发布页只有 tag、没有发行包，而 CI 全绿」。
- **`CheckScripts.py` 不再把「刚构建出来的 `Dist/AhaCli.*`」当成被忽略的源码（issue #262，2026-10-17）**：
  `check_ignored_sources()` 用 `git ls-files --others --ignored` 找「被 `.gitignore` 吃掉、却被装配描述符
  按名字引用」的文件。包根扁平化（#217/#218）之后 assemblies 会把**受跟踪**的 `bin/AhaCli.sh` /
  `bin/AhaCli.bat` 铺到 `Dist/` 根，而 `Dist/` 整体被忽略 ⇒ `clean verify` **之后**的检查必然假红两条
  （干净检出上不出现，所以本机一直绿；`dev` 上的 `Gate` 自 2026-10-15 起一直红，修完 #260 才轮到它）。
  修法只加一条白名单：**同名文件在仓库别处受跟踪 ⇒ 视为构建产物**，不判红；只在被忽略目录里存在、
  仓库别处没有同名文件的手写脚本（`Dist/apply-update.cmd` 那种形态）**仍照旧判红**（已用反向用例钉住）。
- **`ProjectVersion.py --verify` 的产物名判定修掉假红（issue #260，2026-10-17）**：包根扁平化
  （#217/#218）之后 `Dist/aha-cli.jar` / `Dist/aha-desktop.jar` **不带版本号**，而校验用
  「文件名以 `artifactId-` 开头」判定「是不是本项目的版本化产物」——根聚合工程的 artifactId 恰好是
  `aha`，于是 `aha-cli.jar` 命中前缀 `aha-`，被判成「`aha` 的产物但没有版本段」，报
  `❌ 产物文件名中的版本段：Dist/aha-cli.jar（版本段 未识别）`。后果是 `dev` 上的 `Gate`
  自 2026-10-15 起**一直红**（`Gate` 是 `main` 的必需检查 ⇒ 任何合 `main` 的 PR 都被挡住）。
  三处收口：① `collect_artifact_ids()` 不再把根聚合工程（`<packaging>pom</packaging>`，不出产物）
  计入前缀表；② `extract_version()` 要求版本段**紧跟** artifactId（`match` 而非 `search`），
  于是「以 `artifactId-` 开头但后面不是版本号」的扁平产物按「不带版本号」跳过，并写进校验日志
  （`…；跳过 N 个不带版本号的产物（如 Dist/aha-cli.jar）`）；③ `--self-test` 增补 8 条产物名判定
  用例（含 `aha-cli.jar` / `aha-cli-native.jar` / `aha-0.1.3.jar` 三个反例），正反例一起跑。
  真实漂移仍会被抓（`aha-cli-0.1.2.jar`、`aha-core/target/aha-core-0.1.2.jar` 均判红，已在
  `/tmp` 副本上前后对比验证）。
- **原生形态的应用内升级被误拦（issue #51，2026-10-16 真机追问）**：上一轮为修「原生 exe 下错包」，我顺手加了一层「原生形态不支持自更新」的门，理由是「原生包不在升级通道里」。**实测该理由不成立**：原生线有自己的 tag（`V<版本>-aha-desktop-native`）与资产（`AHA-Desktop-Native-<版本>-<平台>-jdk<JDK>.zip`），都在 `ChannelContract` 里；`supports()` 只排除「原生包 + Stable 通道」（原生线还没发正式版）；换装与重启机制与 JVM 同构（包内清单 + helper 随包分发 + `restartCommand()` 返回 exe）。真正缺的只有**形态判定**（原生 exe 被判成 JVM 形态 —— 那是真 bug，已修）。处理：**撤掉那层门**，原生形态恢复走同一条升级链路；新增正向用例 `UpdateServiceTest#prepareSwapPlansToReplaceTheNativeExecutable`（此前这条路径零覆盖：计划里要出现 exe 的替换动作、`restart` 指向 exe）；文档 / TS-2610-047 / TD-00022 / #247 的结论一并更正。真机验收项：**替换正在运行的 exe** 后能否自动重启到新版本。
- **升级 tab：「升级」按钮一直是灰的、选中版本也不变（issue #51，2026-10-16 真机反馈）**：这其实是**门生效时的预期行为** —— 换装成功后回不来的形态（原生镜像、只剩单体 jar、非 Windows）宁可不换，所以 `onSelectionChanged` 遇到门生效就直接置灰并 `return`，**选中任何一行都不会变可点**。问题出在**说明看不见**：原因只写在底部那行**灰色**状态文字里，用户把它当成了按钮故障。修复：门生效时在内容区上方显示一条**加粗、常显**的提示（`aha.upgrade.notice`），写出原因并明确「版本列表只是让你看看有哪些版本」；版本列表占位符改成同一口径；「检查更新」仍可用（便于浏览版本）这一既有决定保留。另按门的**性质**分：**原生镜像形态**下连「检查更新」与通道单选项一起禁用（升级通道里只有 JVM 线资产，列出来也装不了、还会诱导去点升级）；**其它门**（POSIX 未实现 / 只剩单体 jar）仍保留列表以便据此手动取件。冒烟断言补齐（提示可见 + 占位；非原生门下「检查更新」仍可用不被顺手改掉）。教训写进设计文档：**置灰必须配看得见的原因**。
- **CI：Maven Wrapper 下载发行包瞬时失败不重试（2026-10-16 实测）**：PR 的 Windows 腿 21 秒就红，报 `Conversion from JSON failed with error: Invalid character after parsing property name` + `java.io.FileNotFoundException: …/apache-maven-4.0.0-rc-7-bin.zip` —— 那次 Central 返回的不是元数据（被挡 / 挑战页），wrapper 拉不到发行包；而 `.github/actions/maven-run` 的瞬时失败判据一条都不命中 ⇒ **一次都没重试**直接失败，**同一个提交的另一次运行里同一条腿是绿的**（同一提交两种结果，正是该 action 要覆盖的场景）。修复：判据补 `Exception in thread "main" java.io.FileNotFoundException: https://repo.maven.apache.org/maven2/org/apache/maven/…`、`apache-maven-<版本>-bin.zip`（**版本号可能带后缀，如 `4.0.0-rc-7`**）以及 wrapper 其它措辞（`Conversion from JSON failed` / `Error downloading` / `Could not download Maven distribution` / `Connection timed out`）。**第一版判据写错了**：`apache-maven-[0-9][0-9.]*-bin\.zip` 匹配不到带后缀的版本，而当时的「正向验证」因为多行样本里**另一条判据也命中**而没暴露 —— 现改为**逐条**对着日志里那一行验证（命中真实报错行；编译错误与测试失败一条都不命中，真失败仍立刻失败）。规范同步 `BuildSpec.md` v1.46.0 §8.1 的判别表（含这两条教训）。
- **原生镜像的 exe 把自己认成 JVM 便携包 ⇒ 点升级去下 JVM 包、exe 自己没变（issue #51，2026-10-16 真机）**：原生镜像里 `getProtectionDomain().getCodeSource()` **不是 null**，它指向**运行中的 exe 自己**；而形态判定用的是「code source 是不是**普通文件**」⇒ `jarBased=true`。触发条件正是最普通的那种用法：安装根里同时有（先前取来的）`aha-desktop.jar` 与（后来放进去的）`AhaDesktopNative.exe`，两种形态并存时必现；资产前缀由形态决定 ⇒ 必然挑到 JVM 包。修复：① `InstallLayout` 先看 GraalVM 运行期属性 `org.graalvm.nativeimage.imagecode`（非空即原生），且 `of(Path)` 只把 **`.jar`** 文件认成 JVM 便携包；② 能力门新增**原生镜像**一条并**排在平台那条之前**（在 Linux/macOS 上不该只被告知「只在 Windows 上实现」），界面与 CLI 在**下载前**置灰并说明「到发布页取原生包手动覆盖」；③ `UpdateService.prepareSwap` 加同一道兜底。真正实现原生自更新另立 [#247](https://github.com/ACANX/AHA/issues/247)（TD-00022）。排查记录：[TS-2610-047](Docs/Troubleshooting/TS-2610-047-Native-Exe-Mistaken-For-Jvm-Package.md)。
- **快照取件缺「平铺到 `Dist\` 根」这一步（issue #244，2026-10-16 真机）**：双击 `Dist\UpdateDesktopJvmSnapshot.cmd`
  能下载、能解压，但 `aha-desktop.jar` 与启动脚本躺在 `Dist\<资产名>\` 子目录里，`Dist\` 根下什么都没有 ——
  「取件」只完成一半，还得手工进去搬。而正式版取件（`UpdateJvm.ps1`）一直是「解压 → 平铺到落地根」，
  同一个动作两种结果。修复：新增 `Dist/UpdateInstall.ps1`（平铺步骤，**先探测再动手**：目标被占用时落地根一个字节都不改），
  `UpdateSnapshot.ps1` 对 JVM 线（桌面端 / CLI）平铺到 `-Out` 根并把被覆盖的既有文件备份为 `<名字>.bak`，
  铺好后清理暂存目录（新增 `-KeepExtract`；`-NoInstall` 仍是「只取件」）。
  验证方式：**取一次件即可确认**（真机，见 issue #244 的验收清单）—— 不为此在每次构建里加自测步骤。
- **设置面板观感与可用性三处（issue #51，2026-10-16 真机反馈）**：① **通道区收成一行** —— 标题文案改为「版本发布通道」（原「（决定从哪条发布线取版本）」多余），并与三个单选项同一行：标题在左、选项在右；② **「当前版本」行支持复制版本号** —— 该行由 `Label` 换成只读 `TextArea`（可拖选 + Ctrl/Cmd+C），右键另有直达入口「复制版本号」（只复制版本号本身）与「复制选中 / 全选」；③ **设置面板宽高可调** —— `Dialog` 默认不可缩放，版本列表一长只能在固定小块里滚动；现在放开缩放，并让根 VBox 与 `TabPane` 的 max 一并放开、`TabPane` 在根里 `vgrow`，做到「拉大窗口 ⇒ 版本列表跟着长」（最小 480 × 360，窗口大小暂不记忆）。冒烟断言分别钉住：标题文案与「同处一行」、当前版本行是只读 `TextArea` 且能整行选中 + 有「复制版本号」入口、对话框 `isResizable()` 与 `tabs.getMaxHeight() == Double.MAX_VALUE`。
- **原生包（`AhaDesktopNative.exe`）点「检查更新」抛 `UnsupportedFeatureError: Record components not available for record class ReleaseInfo`（issue #51，2026-10-16 真机）**：Jackson 反序列化 record 时要读它的 **record components**（`Class.getRecordComponents()`），而原生镜像（closed-world）要求**构建期**就把该 record 的访问器登记好 —— `core.update` 下的 DTO 一条都没登记，于是「检查更新」在原生包里必崩；而 **JVM 版、单测、CI 冒烟全都跑在 JVM 上**（反射可用），三方全绿，藏住了这个依赖。修复：把 `core.update` 下**全部 21 个 record / enum** 逐条登记进两个原生包的 `reachability-metadata.json`（桌面端与 CLI 共用 `aha-core`，两边都要）；新增守卫 `NativeImageMetadataTest#everyUpdateRecordAndEnumIsRegisteredForReflection`（同时扫源码与两份元数据，新增 record 忘了登记就在 Build/Gate 阶段变红，已做反向注入验证）。
- **升级结果那行文字选不中也复制不出来（issue #51，2026-10-16 真机复测）**：上一轮的做法是「给 `Label` 加右键菜单」，真机反馈**还是选不中、也复制不出来** —— 菜单这一层不符合「想选中一段文字」的直觉。修复：升级页「升级」按钮下方那行状态信息由 `Label` 换成**只读 `TextArea`**，可以直接拖选，选中按 Ctrl/Cmd+C 即复制；右键菜单保留但降为次要入口，规则改为「有选中就复制选中，没选中则整条」，并新增「全选」。教训写进设计文档：**要能选中就换控件，给不可选中的控件加菜单解决不了问题**。
- **从较旧的安装升级时报「安装根里没有换装 helper（该包不完整，无法自更新）」（issue #51，2026-10-16 真机实测）**：换装脚本（`apply-update.cmd` / `AhaApplyUpdate.ps1`）是**随包分发**的，所以新包里一定带着；但程序只从「安装根」（正在运行的那一版）里找 —— 如果那一版是 helper 随包分发之前打的包（真机就是 10161957 → 10162016 这个组合），安装根里什么都没有，于是报错，而**锅被扣在一个其实完整的新包上**（已核对包内容：两个脚本都在包根），升级也卡死。修复：`installHelpers` 改成**先看新包、再看安装根**，从任意旧安装升级都能走通；文案也改准确（列出两处路径 + 「请手动覆盖安装一次」），不再说「该包不完整」。
- **界面上报错复制不出来（issue #51，2026-10-16 真机反馈）**：JavaFX 里**非可编辑**的 `TextArea` 没有默认右键菜单（默认菜单只给可编辑控件），而 `Label` 既不能选中也没有菜单 ⇒ 日志区的右键、升级页状态行里那句失败信息、消息流里的报错，都只能靠按钮或手打。修复：四处加右键复制 —— 日志面板文本区（复制选中 / 复制全部 / 全选，另加 Ctrl/Cmd+C）、升级页状态行（复制这条信息；另附一键复制「版本 / 形态 / 平台 / 通道 / 状态 / 日志位置」）、消息流每条消息「复制这条」、滚动区「复制全部消息」；消息流的文字收集收敛成 `DesktopShell#collectText` 一份实现（界面复制与冒烟断言共用，避免两边读法不一致）。
- **应用内升级下载失败：「跳转目标的主机不在白名单 release-assets.githubusercontent.com」（issue #51，2026-10-16 真机实测）**：GitHub 把发布资产换到了新的 CDN 主机（`objects.githubusercontent.com` → `release-assets.githubusercontent.com`），而白名单只列了旧的那个 ⇒ 用户点升级后直接失败在跳转白名单上。修复：改成「显式主机 + **GitHub 资产 CDN 域名 `*.githubusercontent.com` 的子域**」两条规则（该域名归 GitHub 所有，放行子域不会把包交给第三方；只补一个主机名的话，下次 CDN 换名又会坏在用户那里），并把错误信息写成可行动的（写明允许范围 + 建议手动下载）。mock 服务器给不出真实跳转，所以规则本身由单测钉住（含 `githubusercontent.com.evil.com` 这类相似域名的反例）。
- **升级面板：切换通道后「检查更新」点不动 + 通道区没有标题/紧贴 tab（issue #51，2026-10-16 真机反馈）**：打开设置面板会自动检查一次，检查期间「检查更新」置灰；用户这期间换通道后，要等**旧通道那次检查**跑完（网络慢时最多 30 秒）才能再点，看起来就是「卡住了」。修复：切通道（或开始换装）**立刻作废在飞的检查并解禁按钮**（`UpgradePane.checkGeneration`，旧结果直接丢弃，不会用旧通道的列表盖住新通道）；收尾放进 `finally`（`busy` 卡住一次用户就再也点不动）；检查期间显示进度条并明说「最多等 30 秒」。观感：通道区加标题（`aha.upgrade.channel.title`，「发布通道（决定从哪条发布线取版本）」）、三个单选项各给 id，内容区加内边距与行距 —— 原来那行紧贴 tab 标题，也看不出三个选项是干什么的。
- **桌面端应用内升级：清理判据升级为「进程号 + 文件年龄」两道（issue #51，2026-10-16）**：启动收尾原本靠「标记文件的修改时间在 10 分钟内」猜「换装还在不在跑」——猜错成「不在跑」就会删掉换装正用的文件（大包 + 慢盘 + 杀毒扫描超过 10 分钟时）。现在：① helper 把自己的进程号写进 `helper-started`，启动收尾直接问系统「这个进程还活着吗」（准确，不用猜）；② `StagingArea` 加**年龄下限 10 分钟**：比这更新的文件/目录一律不删，残留下次启动再清（**这正是「升级完不立刻删 `.zip`」的通用化**）；③ 老格式标记（只有时间戳）按新鲜度兜底；④ 硬顶 6 小时，避免进程号被复用后残留永远清不掉。界面会把「有不到 10 分钟的残留刻意没动」如实说出来。
- **桌面端应用内升级：第二轮代码审查的 P1/P2 优化（issue #51，2026-10-16）**：按第二轮审查逐条修：
  - **P1 启动收尾会把「正在进行的换装」清掉**：`onStartup` 每次启动都无条件清其它版本暂存与旧备份 —— 用户在换装那几秒里再启动一次 App，就会删掉 helper 正要用解包目录（报「新包里没有这个文件」，把锅甩给包），`BackupKeep=0` 时还会删掉回滚基准。修复：先判「有没有换装在飞」（计划在 + 启动标记是 10 分钟内写的 + 还没回执）⇒ 什么都不删、连计划也不删，只把这件事说出来；标记过期才按残留清理并如实报告；
  - **P1 `restart` 契约与 jar 兜底不一致**：计划里 `restart` 的语义是「每个元素 = 一个可执行文件」，而兜底塞的是 `[java, -jar, jar]` —— 过不了绝对路径校验，计划根本写不出来（上一轮只修了「第一个元素不绝对」，还写了个只断言列表形状的测试把它固化了）。修复：**删掉兜底**，改用安装形态门（`UpdateLauncher.unsupportedReason(layout)`）在下载前拦住「只剩单体 jar」的布局——宁可不换，也不换完不回来；
  - **P2 清理计数是编的**：以前固定 `+1`，什么都没清也说「清理 1 个暂存目录」；`StartupReport.warning` 在生产代码里没人用。修复：`StagingArea.clear` 返回「真的删了没有」，计数如实，`warning` 写进消息流；
  - **P2 helper 用完不消失**：文档 §8.2 第 5 项的验收口径是「两个 helper 文件消失」，而 `.cmd` 是正在运行的批处理、删不掉。修复：启动收尾补刀删掉两个 helper 与 `helper-started` 标记；
  - **P2 一键入口从没被真跑过**：`native-update-script` 在 ubuntu 的 pwsh 上，`%~dp0` / `%~1` / 退出码传递零覆盖。修复：自测脚本新增「经 `Dist/apply-update.cmd` 换装」用例，并在 Windows 腿上真跑一遍（顺带覆盖 Windows PowerShell 5.1 的 BOM / 编码路径）；
  - **P2 helper 无条件信任计划**：Java 侧有 `PathSafety`，但 helper 读的是 JSON、不经过 Java。修复：helper 自己校验（路径必须绝对、动作必须落在安装根内、拒绝 `..`），自测新增「动作逃出安装根 ⇒ 拒绝」用例；
  - **P2 守卫自身有洞也会误报**：`check_repo_script_steps_have_checkout()` 跳过以 `run:` 开头的行 ⇒ **单行** `run: python3 …` 漏检；反过来 `echo "… python3 bin/CheckDocs.py …"` 会误报。修复：剥掉 `- run:` / `run:` 前缀后只看命令本身，排除 `echo` / `printf` / YAML 键，并删掉死条件；
  - **产品级问题（CI 的 Windows 腿抓到）**：helper（`cmd → powershell 5.1`）里用 `Get-FileHash` 算哈希，而**调用者**（自测、或用户从 pwsh 7 控制台起 App）留下的 `PSModulePath` 会让 5.1 加载不到 `Microsoft.PowerShell.Utility` ⇒ 报「不是 cmdlet」⇒ 逐文件自证失败 ⇒ 回滚（回滚把失败兜住了，但一次本该成功的换装变成了失败）。修复：改用 .NET（`SHA256.Create()` + 流）算哈希，helper 不再依赖调用者的 PowerShell 环境；
  - **P3**：并发换装加互斥（`prepareSwap` 拒绝第二个 helper）；退出前那句 `appendNotice` 看不到 → 改为只记日志；`helperNeverRan` 文案区分「只暂存没执行」；`.part` 不再是「可续传」而是排查现场（重试重新下载）；「切回上一版」记入 `TD-00021`。
- **桌面端原生镜像：设置面板打不开（FXML 里的枚举属性，2026-10-16 真机自测）**：`settings-dialog.fxml` 的 TabPane 写了 `tabClosingPolicy="UNAVAILABLE"`。JVM 上没问题（`FXMLLoader` 用反射转换枚举、反射可用），但原生镜像属 closed-world：该类型未登记在 `reachability-metadata.json`，转换直接抛 `Unable to coerce UNAVAILABLE to class TabPane$TabClosingPolicy`，用户点「设置」**什么都没发生**。修复：枚举改为在 Java 里设（`SettingsDialog#wireTabs`）；新增守卫 `NativeImageMetadataTest#fxmlNeverSetsEnumValuedAttributes`（扫全部 `.fxml`，出现「大写枚举值」即失败并给出改法）；并把「设置面板装载失败」兜住 → 在消息流里说明原因，不再是一条未捕获异常；规则写进 `DesktopNativeDesign.md` R12。
- **桌面端应用内升级：代码审查后的 P0/P1 修复（issue #51，2026-10-16）**：对 PR #243 做了一次全量自查，发现**功能实际不可用而门禁全绿**，逐条修复：
  - **P0 换装入口根本没进仓库**：`Dist/apply-update.cmd` 被 `.gitignore` 的 `Dist/*` 吃掉（`git add -A` 静默跳过、`git status` 干净），因此打出来的包里没有它，应用内升级在「复制 helper」这一步**必然失败**。修复：`.gitignore` 放行该文件、文件本体改为**纯 ASCII + CRLF**（中文注释移出）、`maven-assembly` 的静默跳过由**新守卫**接住；
  - **P0 原生包不带 helper**：`dist-native.xml` 只打「exe + README + 参数 + 许可证」，于是「Windows 原生形态自更新」这条路**注定失败**（它正是 §8.2 第 9 项待验内容）。修复：原生包也把两个 helper 铺到包根；
  - **P0 守卫缺口（会再犯）**：新增 `check_update_helper_packaging()`（两条发行线都要收 helper、helper 必须存在**且被 git 跟踪**、Java 与 PowerShell 的「已启动」标记名逐字一致）、`check_repo_script_steps_have_checkout()`（作业调仓库脚本必须先 checkout）、并扩展 `check_ignored_sources()` 识别「**被 .gitignore 忽略却被装配描述符 / 工作流按名字引用**」的文件；每条都做了**正向 + 反向注入**验证；
  - **P1 失败静默**：① helper 自证失败分支的回滚没包 `try/catch` —— 回滚一抛异常整个脚本就崩，不写回执也不重启；② App 拉起 helper 后**不等它起来**就退出，helper 被安全策略拦下时用户看到的是「App 关了、什么都没发生」。修复：helper 第一件事写 `~/.aha/update/helper-started`，App 等它出现（默认 20s）才退出，等不到就留在前台报错（新错误码 `UPDATE_HELPER_NOT_STARTED`）；
  - **P1 回执与清理没有调用点**：`consumeLastResult()` / `cleanupAfterSwap()` 在生产代码里从未被调用 —— 回执从不展示、`pkg.zip` 与解包副本（每份 50–110 MB）永不清理、`Update.BackupKeep` 是死配置。修复：新增 `UpdateService.onStartup()`，桌面端启动时在虚拟线程里收尾并把结果告诉用户（新增 `AhaDesktopSmokeTest#startupShowsTheLastSwapResultAndCleansStaging` 真验证）；
  - **P1 能力门挡得太晚**：Linux / macOS（POSIX 换装脚本尚未实现，见 TD-00020）与原生形态，界面允许走完「检查 → 下载 → 解包」才报「该包不完整」，把锅甩给包。修复：`UpdateLauncher.unsupportedReason()` + 界面**下载前**置灰并说明；
  - **P1 计划校验自相矛盾**：`UpdatePlan` 要求拉起命令是绝对路径，而 `InstallLayout` 的兜底是裸 `java` ⇒ 在「只有 jar」的布局上自更新直接不可用。修复：兜底改用 `<java.home>/bin/java[.exe]`；
  - **P2**：`startSwap` 只 catch `UpdateException`（其它异常会让界面永久 busy）、`prepareSwap` 先写计划再装 helper（失败留残计划）、重定向允许 `https → http` 降级、`COPY_ATTRIBUTES` 可能把只读属性带到落地文件、「未提供校验和」没有照实告知；
  - **CI 修复**：`native-publish` / `cli-native-publish` / `build-publish` 三个发布作业**没有 checkout** 却调用 `PackageManifest.py`，CI 报 `No such file or directory`；
  - **新增真实行为测试**：`Script/PowerShell/HelperSelfTest.ps1` 在临时目录里真跑换装 helper 的失败路径与成功路径（此前只有「写没写出来」的静态检查）；
  - 契约与验证记录同步：[DesktopAutoUpdateDesign.md](Docs/Design/DesktopAutoUpdateDesign.md) v1.1.0（§4.4 握手与收尾、§5.4 能力门、§8.1/§8.2）、[ReleaseProcess.md](Docs/DevSpec/ReleaseProcess.md) v1.30.0 §3.6、[BuildSpec.md](Docs/DevSpec/BuildSpec.md) v1.45.0。

- **`/snapshot-assert` 永远找不到 tag + PR 留痕评论 403（issue #166 实测）**：取发布页 tag 的 jq 表达式写成了 `.[].tagName`（GitHub 的字段是 `tag_name`），结果恒为空 ⇒ 命令总是被当成「发布页上没有对应 tag」拒绝，status 与留痕都产生不了（PR #233 实测）。修为 `--jq '.[].tag_name'`。
  同一轮实测还查明**写 PR 评论的权限**：只给 `issues: write` 时 `POST /repos/{}/issues/{}/comments` 仍然 **403**（`Resource not accessible by integration`）—— GITHUB_TOKEN 对 **PR 对话**的写权限要 `pull-requests: write`。`SnapshotAssert.yml` 与新的 `PreDeployVerify.yml` 一并补齐（后者首次运行实测：status 成功、评论 403；补齐后正常）。
- **CLI 原生镜像 `--help` 退出码非 0（issue #117，2026-10-16）**：三平台一致失败——`--help` 渲染 picocli 用法表格时经 `BreakIterator` 读 JDK 的 `sun/text/resources/LineBreakIteratorData`，而该资源**没有扩展名**，按扩展名的 `-H:IncludeResources` 正则匹不上，缺了就在运行期抛 `InternalError: Can't load sun/text/resources/LineBreakIteratorData`（**构建全绿、`--version` 正常**，故三平台一致而非平台差异）。照桌面端的既有做法，给两份 CLI 参数文件（JDK 25 基线 + JDK 27 变体）补上该资源与 `.*\.icu$`；结论登记进 `BuildSpec.md` §10.2 与 `CliNativeDesign.md` §4.3 / R3。
- **原生 exe 里输入框的轮廓线丢失（issue #229，2026-10-15）**：只在 **原生镜像编出的 exe** 里，切换主题后对话输入框的轮廓线不见了 —— 而内容面底色按 #153 的口径与页底**同色**，轮廓线一没就分不出输入框的范围（JVM 模式正常）。根因：轮廓线此前**完全依赖 CSS**，而原生镜像下 modena 那些带 `derive()` / `linear-gradient()` 的规则会**整条声明被丢弃**（`TS-2610-029`）；`ThemePaint` 的属性 API 兜底只覆盖背景与文字，**边框漏在覆盖范围之外**。
  - 修复：`ThemePaint` 新增 `paintBorder()`，把 `-fx-border-color`（+ 宽度 / 圆角，缺省 1 / 3）落到 `Region.setBorder(...)`；三处同类控件（对话输入框、会话搜索框、日志面板文本域）的内联样式补上轮廓线（值取自补丁样式表，JVM 下外观不变）。约定：样式串没写边框就不动现有边框，写 `transparent` / `none` 才清空。
  - 断言：冒烟测试读输入框**实际生效的** `Region.getBorder()`，断言颜色为 `Palette.BORDER` 且**跟着主题换**。
  - 记录见 [`TS-2610-046`](Docs/Troubleshooting/TS-2610-046-Input-Outline-Lost-In-Native.md)（含结论分层 / 观测数据 / 排除项 / 未决问题）。
- **下拉列表里当前项「看不见，也没完全隐藏」（issue #227，2026-10-15）**：ComboBox 展开后，**当前值那一行**是白底白字（真机截图：三个选项里中间一行只有背景没有文字）。根因是** CSS 特异性失衡**：基面规则 `.list-view > .virtual-flow > .clipped-container > .sheet > .list-cell` 是 **5 个类**，把 `.list-cell:filled:selected`（1 类 + 2 伪类）的强调色背景盖掉了；而选中态**文字**色来自更具体的 `.list-cell:filled:selected .text`（4）却能生效 ⇒ 亮色主题下基面底色与 `ON_ACCENT` 同为 `#FFFFFF`，两者同色。
  - 修复：两份主题模板的状态规则**加上与基面完全相同的选择器前缀**（同特异性时后者胜），弹出层再加一层 `.combo-box-popup > …` 留余量；通用规则保留。
  - 新守卫：`bin/CheckTheme.py` 第 8 项 `check_state_rules_not_shadowed()` —— 对每一条设背景色的基面规则，要求存在**类路径完全相同**的状态规则（更深的规则不能代表普通列表，反向验证 2/1 处如期被拦下）。
  - 新断言：`AhaDesktopSmokeTest#listSelectionSurfaceIsReadableInBothThemes` —— 真开列表，读选中格**实际画出来的**背景色与 `.text` 填充色，断言分别为 `FOCUS_BORDER` / `ON_ACCENT` 且**不相同**（亮 / 暗各一次）。
  - **原生包自测确认（2026-10-15，ACANX）**：下拉列表当前项正常显示（强调色底 + 白字）——
    这次的 CSS 特异性问题在原生镜像下同样按 CSS 规则生效，无需额外补属性 API 兜底。
  - 为什么现在才暴露：会话列表单元格是**在 Java 里显式上色**的（`DesktopShell.SessionCell` → `ThemePaint.themed`），不过 CSS；下拉列表是第一个「颜色完全交给样式表」的 `ListView`。记录见 [`TS-2610-045`](Docs/Troubleshooting/TS-2610-045-Selected-Row-Invisible-By-Css-Specificity.md)。
- **桌面端切主题后日志区与输入框底色不跟着变（issue #153 复现，2026-10-15）**：根因是**可见底色由
  皮肤内部节点绘制**（`TextArea` 的 `.content`、`ScrollPane` 的 `.viewport` / `.corner`），而
  上色逻辑只作用于**控件本体**（`setStyle` + 属性 API），内部基面此前只依赖补丁样式表 ——
  于是「切换时本体换了、基面停在旧色」，而重启整棵重建又正常。
  **ACANX 实测对照：JVM 包正常、只有原生镜像包复现** ⇒ 范围钉死在「原生镜像下样式表对内部节点的
  热替换/查表不可靠」（同 `TS-2610-028` 家族），也说明本体走属性 API 那条路两边都可靠。
  修法：`ThemePaint` 新增 `SKIN_SURFACE_SELECTORS` + `applySkinSurfaces()`，把同一份底色用属性 API
  也写到内部基面上（延后一帧、先 `applyCss()`）；`install()` 的样式表清理改为按文件名删掉非当前主题的
  补丁表（URL 形式不一致时原先删不掉，会留下两份、切回旧主题获胜的是旧那份）。
  回归断言：冒烟测试在切亮/暗后直接读 `.content` 的实际背景色比对 `Palette.CONTROL_INNER`；
  `bin/CheckTheme.py` 新增守卫，代码清单与两份表漂移即红。
- **切主题后输入框 / 对话框组件的颜色不跟着变（2026-10-14，issue #153）**：颜色是**内联在样式串里**的，
  换色表不会自动影响已建节点 ⇒ 主窗口靠 `DesktopShell.restylers` 登记重刷，但主窗口之外与
  「一次性上色」的路径**不在重刷范围内**。本轮查出四类并存漏洞：① `static final String` 样式常量
  （常量在**类加载时**求值，颜色被**冻死**——重开对话框也还是旧色）——
  `ProviderDialog.SECTION_TITLE_STYLE` / `SettingsDialog.MUTED_STYLE` / `NOTE_STYLE` /
  `ApprovalDialog.SECTION_STYLE`；② 对话框内容用 `ThemePaint.themed(node, Palette.dialogTheme())`
  这种**已求值字符串**一次性上色；③ 打开中的对话框**场景**不换样式表；
  ④ `ThemePaint.apply()` 的 API 兜底缺 `-fx-control-inner-background`（输入类控件的底色来源）。
  修法：`ThemePaint` 新增 `onThemeChange` / `refreshAll` / 对话框面板登记表（弱引用）/
  `themedForever(node, supplier)`；`DesktopShell.applyTheme` 调 `refreshAll`；
  4 个静态常量→按需求值；对话框内容与日志 `stats` 登记重刷；`apply()` 补上那一条兜底。
  **并加机器守卫**：`bin/CheckTheme.py` 第 6 项禁止 `static final String` 里出现 `Palette.`
  （反向验证：注入一个 `BAD_STYLE` 即被点名）。详见 `TS-2610-038`。
- **原生桌面 exe 的 `OS Information:` 报 `Windows 8.0 build 9200`（2026-10-14，issue #151）**：
  机理不是「读错字段」——`GetVersionEx` / `GetVersion` 家族在**没有 Windows 10 兼容清单**的
  可执行文件里一律返回 **6.2.9200**（Windows 的既定兼容性行为，与真实版本无关）；`java.exe`
  自带清单所以 JVM 模式正常，GraalVM 生成的 exe 默认没有。修法：新增
  `aha-desktop-native/src/native/app.manifest`（只声明 Win10 / 11 的 `supportedOS`，**纯 ASCII**
  ——mt.exe 是旧式 MS 工具，不给编码留赌注），Windows 腿用
  `-H:NativeLinkerOption=/MANIFEST:EMBED` + `…/MANIFESTINPUT:<绝对路径>` 嵌进 exe；
  「平台专属」由 Maven 表达（`native-windows-manifest` profile 只改一个属性，非 Windows 用
  只有注释的空档参数文件），Linux / macOS 腿不受影响。两个坑记进 `TS-2610-037`：
  ① **清单路径必须绝对**——native-image 以**它自己的构建临时目录**（`%TEMP%\SVM-…`）为
  工作目录调链接器，相对路径会被解析到那里（第一次尝试因此让 Windows 原生构建整条失败）；
  ② 那次失败被步骤级 `continue-on-error` 藏住（`conclusion=success` 而 `outcome=failure`），
  CI 全绿而清单自证与 OS 断言**全被跳过**——有容错的步骤必须看 outcome。
  CI 新增两道门禁：**清单自证**（从 zip 里的 exe 搜 Win10 GUID）+ 冒烟断言
  （`Windows version 8.0 build 9200` 出现即失败、必须命中 `Windows version 10.`，
  并要求先打印 `OS Information:` 以免空过）。真机复核待 ACANX。
- **暗色下日志区 / 对话输入框的底色与其他页面、对话框不一致（2026-10-13，issue #153）**：
  根因**不是**色号漂移（实测两份主题补丁表选择器 **42/42 一致**、色号 **0 个野生**），
  而是 **一个常量承担了两种语义**：`CONTROL_INNER` 同时用于「内容面」（输入框 / 文本域 / 列表 /
  滚动容器）与「抬起控件」（按钮 / 菜单 / 弹层），于是**大块内容面**在暗色下也亮着一档。
  有意思的是两主题的感知差几乎相同（ΔL\* 暗 **3.45** / 亮 **3.81**），但深色底上这一档被放大感知
  ⇒ 只有暗色被报上来。修复：**按角色拆开**——内容面对齐页底（`CONTROL_INNER` 暗色 → `#1E1E1E` = `BASE`），
  新增 `RAISED_BACKGROUND`（`#252526`）给抬起控件；**亮色不动**（未报问题）。
  同时把 `DesktopDesign.md` §11.6 早就写下的「**不允许硬编码颜色**」变成机器强制：
  新增 `bin/CheckTheme.py`（Gate 里跑）——两份表的选择器 / 属性集合必须一致，
  且每个色号必须属于该主题的色表；注入野生色号或单边删选择器都会如期失败。详见 `TS-2610-035`。
- **原生镜像 OS / D3D 驱动信息全零（2026-10-13，issue #60）**：`prism.verbose` 打印的
  `Windows version 0.0 build 0`、`Driver null, version 0.0.0.0`、`Device : ven_0000, dev_0000, subsys_00000000`、
  `Max Multisamples supported: 0` —— 根因不是「注册表 / WMI 读不到」，而是 **native 按名回写字段**：
  `D3DPipeline.printDriverInformation()` 调 native `nGetDriverInformation(int, D3DDriverInformation)`，
  由 `prism_d3d.dll` 用 `GetFieldID` 按名回写该对象的字段，而 `com.sun.prism.d3d.D3DDriverInformation`
  及其字段在**两份元数据里都是 0 条登记** ⇒ 回写不发生、字段保持 Java 默认值（0 / null）=
  日志里那组全零（`getOsVersion()` 的拼接格式逐字吻合）。属 #35 / #37 / #39 同族。
  修复：`jni-config.json` 登记该类的 `allDeclared*`（85 → **86** 条）；
  `NativeImageMetadataTest` 加断言（15 → **16** 条）；工作流 ⑧b 加 `require_jni`；
  **Windows GUI 冒烟加可判别断言**：必须打印 `OS Information:`，且不得出现 `Windows version 0.0 build 0`
  （改前必然出现 ⇒ 真前/后对照，不是空过断言）。**已实测验证**（PR #150 的 Windows 腿）：改前
  `Driver null, version 0.0.0.0` / `ven_0000, dev_0000` ⇒ 改后
  `Driver d3d10warp.dll, version 10.0.26100.33438` / `Pixel Shader version 3.0` / `ven_1414, dev_008C` /
  `Max Multisamples supported: 4`（`1414` = Microsoft、WARP 软件设备 ⇒ 印证 CI runner 无硬件 GPU，
  且**驱动那一组字段也已回写成功**）。⚠ 遗留两点：① 驱动取值需真机对照；
  ② OS 版本虽不再是 `0.0`，但取到 `Windows 8.0 build 9200`——`GetVersionEx` 无 Win10 兼容清单时的
  兼容性撒谎（同日志驱动版本 `10.0.26100.33438` 证明真实版本远高于此）。详见 `Docs/Troubleshooting/TS-2610-034`。
- **原生元数据：Windows 手势 / 拖放两条补登记，并纠正 issue #125 的成因判断（2026-10-13）**：
  核验后确认 **「`reflection` 段缺 `WinGestureSupport` / `WinDnDClipboard`」在本仓库不是缺陷**——
  JavaFX 25 的 win jar 里**没有任何 Java 代码 `Class.forName` 它们**（`javap -p -c` 对
  `WinWindow` / `WinApplication` 实测 **0 处命中**），全 jar 常量池里唯一引用来自 **`glass.dll`**，
  属 native 侧查找，其正确归宿 `jni-config.json` **早已登记**（自 issue #39 起有测试断言守卫）；
  本仓库 CI 的 Windows 腿真启动原生 exe 20 秒（run `38389103313`），stderr 315 B 内
  **无**这两条异常。仍补两条反射登记（473 → **475**）：让这两个类**确定进镜像**、不依赖静态可达链
  （`WinGestureSupport` 在 Java 侧零引用），并对齐姊妹工程按**反射段**生成清单的口径。
  另补三道守卫：`NativeImageMetadataTest` 新断言 + `DesktopNative.yml` 步骤 ⑧ `require_meta` ×2、
  步骤 ⑧b `require_jni` ×2。完整排查与反证见 `Docs/Troubleshooting/TS-2610-033`。
- **FXML 骨架在原生包里启动即崩（2026-10-13）**：`desktop-shell.fxml` 漏写 `<?import?>`，原生包双击后抛
  `LoadException: BorderPane is not a valid type.`——而 `Build`、原生构建、GUI 冒烟**三方全绿**。
  修了三层：① 补 15 个 `<?import?>`；② 新增**静态对账**（元素类型名必须能在 import 表里找到；
  对改前的文件跑同一条规则，15 个类型全部命中）；③ 把原生 GUI 冒烟从「进程存活 20 秒」改为
  **正面断言**——必须命中 `[aha-version]` 与 `[ui-fxml-loaded]`、不得出现 `[ui-fxml-load-failed]`、
  不得在 20 秒内以非 0 退出。另修：`javafx-fxml` 传递进来的 `javafx-controls` 无分类器空壳未排除
  （**306 字节**，而工作流原先的 `-size 0` 判据抓不到），已收紧为 `-size -2k`。
  见 `TS-2610-032-FXML-Missing-Import-And-Smoke-Blind-Spot.md`。
- **启动即输出全量版本（含构建号）**：`main` 的第一件事打印
  `[aha-version] version=0.1.3.00134 baseline=0.1.3 prerelease=true`（纯 ASCII + 显式 flush）。
  走 stdout 而不是日志，是因为 console appender 级别为 ERROR，INFO 级的版本行在真机控制台上看不见
  ——而排障第一句要问的就是「你跑的是哪一版」。它同时是冒烟正面断言的落点。

## [0.1.2] - 2026-10-09

### 变更
- **开源许可切换为 GPL-3.0-or-later**：仓库许可由 Apache License 2.0 改为
  **GNU General Public License v3.0 或更高版本**——`LICENSE` 换为 GPLv3 全文，
  `pom.xml` 的 `<licenses>`、`README.md` 的徐章与许可段、`.agents/skills/*/SKILL.md`
  的 `license` 字段（10 处）同步；`mvnw` / `mvnw.cmd` / `.mvn/wrapper/` 保持原样
  （Maven Wrapper 的官方脚本，许可归 Apache 软件基金会）。
- **版本切换收敛为「改一行」（TD-00016 / issue #92~#97）**：版本号的唯一权威源改为仓库根目录的
  `version` 文件；机器侧只保留根 `pom.xml` 的 `<properties>/<revision>`（8 个子模块写
  `<parent><version>${revision}</version>` 继承），`AppVersion.FALLBACK_VERSION` 与版本解耦
  （固定 `"dev"`）。新增 `Script/Python/VersionDistribute.py` 与 `VersionBump` workflow
  （`workflow_dispatch`），一键把 `version` 分发到 POM 与文档声明并开 PR（分支 `chore/bump-<版本>`）；
  新增 `.github/Python/ProjectVersion.py` 作为版本读取的**唯一入口**，并提供 `--verify` 五处一致性
  校验（`version` 文件 / `<revision>` / `version.properties` / 产物名 / `Build.yml` 的 tag 规则，
  已接入 `Gate.yml`）。文档口径同步：版本切换不再需要手改多处（见 `ReleaseProcess.md` §2 与
  `VersionBumpGuide.md`）。

### 新增
- **供应商内五档模型（issue #67）**：一个供应商可配置 `Ultra` / `Pro` / `Standard` /
  `Flash` / `Fallback` 五档；`Standard` 为唯一必填档，其余可选，缺失时回退 `Standard`。
  - 新增 `ModelTier` 枚举；`ProviderConfig` 增加 `Models` / `DefaultTier`，并把档位解析
    收敛为 `modelFor` / `standardModel` / `effectiveModel` / `tierOf`；
  - 新旧规则以「是否含 `Models`」判定：老配置（只有 `Model`）行为与升级前一致；
    新配置 `DefaultTier` 优先，`Model` 作为默认模型记录并与档位保持一致；
  - 校验：含 `Models` 缺 `Standard`、`DefaultTier` 非法、新规则下 `Model` 不在五档内
    均报 `ConfigException`，并纠正「以 `DefaultTier` 为准」时的不一致日志；
  - **运行期降级**：档位模型不可用（404/422 立即、429/5xx/超时重试耗尽）时，按
    `Standard → Fallback 档 → 全局 Llm.Fallback` 单向降级；401/403 不降级；
    只作用于单次请求，全程 `WARN` 日志，并提供降级监听器供界面提示；
  - 会话级新增 `SessionConfig.Tier`；
  - CLI：`aha provider list/show` 展示五档，`aha provider add` 支持
    `--model-ultra/--model-pro/--model-standard/--model-flash/--model-fallback`
    （`--model` 等价 `--model-standard`）；新增 `aha model use/show`；
    `/model <档位>` 支持会话内切档（老配置维持旧行为）；
    `aha config get Model.Providers.<Id>.Model|DefaultTier|Models.Standard` 可读；
    状态栏显示「供应商 · 档位 · 模型」；
  - 桌面端：`ProviderForm` / `ProviderDialog` 支持五档录入与默认档选择，
    含 `Models` 时 `Standard` 必填校验；
  - 内置 `ModelDefault.yml` 刷新为 8 家五档（新增 `Moonshot` / `MiniMax`）。

## [0.1.1] - 2026-10-08

**无用户可见的功能变更**：版本号由 0.1.0 切到 0.1.1；本版集中修正版本号清单、发布流程与
**按平台出包的机制**，为 0.2 桌面端的发布做准备。

### 新增
- **dev 分支 JVM 便携包自动出包（issue #63 / #65）**：新增独立工作流 `BuildJVMArtifacts.yml`
  （与 `DesktopNative.yml` / `CliNative.yml` 同构）：每次合并到 `dev` 就按「平台 × JDK」构建
  `aha-desktop` 与 `aha-cli` 便携包，发布预发行版（tag `V<版本>-aha-jvm`）；JDK 轴为 **25**（基线，
  release=25）与 **27**（`-jdk27` 后缀，release=27，字节码 major=71）。此前 JVM 模式只有本地构建与
  发版构建两条路径，dev 上每轮合并都无法产出可直接下载验证的包。
- **预发行版显示带构建号的版本（issue #46）**：此前 GUI 与 CLI 只能查到基线版本（如 `0.1.1`），
  而 PR 合并到 `dev` 后自动构建的包带构建号（如 `0.1.1.00046`）——它只出现在产物文件名与包内
  README 上，程序内部查不到。现把版本拆成两层：根 POM 新增 `aha.build.version`
  （默认 `${project.version}`），`version.properties` 增加 `build` 项，`AppVersion` 相应提供
  `buildVersion()` / `isPreRelease()`；`DesktopNative.yml` 与 `CliNative.yml` 在构建时传
  `-Daha.build.version=<a.b.c.PPPPP>`（正式发版不传，等于基线版本，不做强制）。
  展示点：桌面端「关于」与启动日志、CLI 启动页顶栏、`/help`、`aha version` / `--version`。
  详见 `Docs/Troubleshooting/TS-2610-027-Build-Version-Pre-Release.md`。
- **桌面端原生镜像（试验性）**：新增 `aha-desktop-native` 模块与 `.github/workflows/DesktopNative.yml`，
  把 `aha-desktop` 的 JVM 产物再编译成 GraalVM native-image 二进制（win / linux / macos，解压即可双击运行）。
  定位是「验证线」：正式交付仍是 JVM 模式。
  - **四条隔离**：模块只在父 POM 的 `desktop-native` profile 里（默认不在反应堆）；
    独立工作流与作业名；tag 用 `native-v…`（不匹配 `V*`/`v*`，不触发正式发版）；
    `fail-fast: false` + 实验腿 `continue-on-error` + 发布作业 `if: always()`。
  - **每次合并到 dev 自动出包**：版本按 `a.b.c.PPPPP`（PR 号补零到 5 位，如 PR21 → `0.1.1.00021`），
    基线读根 POM、PR 号按提交反查 API；发布为预发行版，可在发布页直接下载。
  - **JDK 25 / JDK 27 两条轴**：`native-jdk27` profile 切换参数文件与产物名，
    **目前只开 Windows**；用于对比 Project Leyden 的 AOT、原始类型预览与 GC 策略对
    启动速度 / 内存占用的影响，为明年适配 JDK 29 铺路。
  - **产物自证**：工作流检查产物存在、体积下限、平台魔法数（PE / ELF / Mach-O）、
    classpath 恰好含 3 个带分类器的 OpenJFX jar；包内附带两份构建参数文件便于事后对账。
- **CLI 原生镜像（试验性）**：新增 `aha-cli-native` 模块与 `.github/workflows/CliNative.yml`，
  把 `aha-cli` 的 JVM 产物再编译成 GraalVM native-image 二进制（win / linux / macos，终端直接可跑）。
  与桌面端是**平行**关系：同一套隔离 / 版本 / 自证方法论，独立模块、独立工作流、独立参数文件，
  两者互不牵连。差异在：无 JavaFX、主类为 `AhaCli`、GC 默认用 serial（短命进程）。
  - **picocli 反射元数据用注解处理器生成**：picocli 不自带 native-image 元数据，本项目在 `aha-cli`
    编译期用 `picocli-codegen` 生成 `META-INF/native-image/picocli-generated/reflect-config.json`
    （实测 30 个类型），比手写清单可靠——子命令 / 选项一变，生成结果跟着源码走；
    `NativeImageMetadataTest` 直接断言生成结果，处理器失效时 Build 阶段即红。
  - **补 JLine 的元数据缺口**：JLine 4 自带元数据，但未覆盖 `org.jline.utils.Signals` 的
    `Class.forName("sun.misc.Signal")`，由本项目元数据补齐（另含 AHA 自身被 Jackson 读写的记录）。
  - **同批修复桌面端参数的一个潜在缺陷**：资源正则漏了 `yaml` / `yml` / `svg`，
    会导致原生镜像启动即报 `CONFIG_NOT_FOUND`（内置 `AhaDefault.yaml` / `ModelDefault.yml` 缺失）；
    两份桌面参数文件已一并修正。
  - 试验性 / 非交付物：真机验证见 `TODO.md` `N-19`。
- **可复用技能 `java-app-graalvm-native-image-compile`**（`.agents/skills/`）：把本项目在
  「JavaFX + JPMS + JNI + 反射 + 多平台分类器」这一复杂场景下编译原生镜像的经验沉淀成技能 ——
  隔离四条、四类清单（初始化时机 / 反射 / 资源 / JNI）、按症状排错、产物自证四项、测量口径，
  外加可拷贝的 POM 骨架、参数起点与工作流骨架。
  技能按「开工前建骨架、边做边改、达成目标才成熟」的方式维护：条目带**状态**（推断 / 已踩坑 /
  已验证 / 已定稿）与**来源**，未验证部分显式标注；当前 `0.1.0`（试验中），
  成熟判据见技能内「用法」一节（含「在别的项目复用过一次」）。
- **可复用技能 `graalvm-reachability-metadata`**（`.agents/skills/`）：把原生镜像的**元数据登记**
  （反射 / JNI / 文件资源 / 运行期初始化）从桌面端与 CLI 两轮实践里抽成**独立的纵向专精技能**：
  三条互补的发现路径（字节码静态审计 / tracing agent / 经验库对照）、来源优先级
  （依赖自带 > 框架生成 > 手写）、精确到方法签名的登记写法，以及四层守卫
  （单测断言 / 产物自证 / 反向验证 / 真机走查 → tracing agent 终局），
  并附常见框架经验库（JavaFX / picocli / JLine / Jackson / sqlite / log4j 等，区分「自带元数据 / 不带」）
  与可复制的元数据模板。与 `java-app-graalvm-native-image-compile`（工程化全流程）互补、互相引用，
  当前 `0.1.0`（试验中）。
  - **tracing agent 采集已制度化**：新增 `references/agent-collection.md`（何时必须跑、自检模式契约、
    多平台合并、过滤规则、CI 接入）与 `scripts/collect-metadata.sh`（`run` / `summarize` / `filter`）；
    并用它对 CLI 与桌面端各采一轮——桌面端据此补进 47 条应用栈元数据（293 → 340）。
- **桌面端 0.2 六项功能**（按用户指定顺序）：
  1. **工具卡片**：默认折叠；展开显示参数与带行号输出（前 200 行并写明总行数）；复制 / 查看全部；
     失败卡片红边、正文摊开并给「重试 / 改参数后重试」——重试交给模型判断，不在本地偷偷重放命令。
     结果行口径与 CLI 一致（不足 100ms 不显示耗时），耗时由注入的计时源保证可确定断言
  2. **会话列表**：左栏列出 SQLite 里的真实会话（标题取用户重命名或首条用户消息、消息数、
     相对时间），当前会话绿点加粗；点选即回放历史（只读，不重新推理）；
     右键重命名 / 导出 Markdown / 导出 JSON / 删除（不可恢复的操作二次确认）
  3. **日志面板**：往 log4j2 根记录器挂内存 appender，实时日志 + 级别过滤；
     过滤在读的时候做，把门槛调回 DEBUG 仍能看到之前的记录
  4. **输入区**：`/` 命令面板（命令名与 CLI 同名，只登记真能执行的命令；命令在本端执行、
     不发给模型）、`@` 文件引用补全（跳过 `.git`/`target`/`node_modules` 等噪音目录）、
     `↑`/`↓` 历史输入（不丢草稿）、`Tab`/`Enter` 接受候选
  5. **授权弹窗**：权限人话说明、完整参数（执行类即完整命令）、按钮上写出被放宽的权限名、
     默认焦点在「拒绝」且 `Esc` 即拒绝、列出本会话已自动允许的权限；
     新增「工具 → 全部撤销本会话授权」（`Ctrl+Shift+R`）
  6. **主题与设置**：暗色 / 亮色 / 跟随系统（换主题会把已建节点一起重刷，不是只换新控件）、
     字号；设置存 `AHA_HOME/desktop.properties`；三条切换路径（`/theme`、视图菜单、设置面板）等价
- **检查守卫两项**：`bin/CheckScripts.py` 新增「源码文件不得被 `.gitignore` 吃掉」；
  `bin/CheckDocs.py` 新增「残留合并冲突标记」。两者都做过反向验证（该拦时拦住、清理后全绿）

### 变更
- **修「全绿却没上传」的真因**：带 `continue-on-error` 的步骤一旦非零退出，
  它的 `outputs` **不会被发布**，下游 `if:` 静默变 false——macOS 腿的「改名 / 上传制品」
  因此被跳过（作业仍是绿的）。修法：诊断步骤开头先写兜底 `produced=false`、
  结尾强制 `exit 0`；执行证据判据从 `*.build_artifacts.txt`（macOS 不产出）改为 `*build-report.*`。
  同时修正构建报告通配——真实文件名是 `<可执行名>-build-report.html`（34~36 MB），
  按 `build-report*` 写会静默漏掉（报告因此一直没进制品）。
- **构建报告作为独立发布物**：native-image 的构建报告**不打进镜像包**
  （镜像是给人运行的，报告是给人分析的），由工作流单独打成
  `AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip`，
  与镜像包**同族命名、同批挂到发布页**，并作为 CI 制品保留 90 天。
  包内含报告 + 镜像参数 + 可达性元数据；产物自证会检查报告包是否真的生成，
  并把报告里的关键数字摘进 Job Summary。
- **按首次真编日志调优 native-image 参数**：删除已弃用且无效的 `no fallback` 开关；
  解锁实验性选项；输出由 `-H:Path` + `-H:Name` 改为 `-o`；
  采纳日志建议的 `--gc=G1`、`--future-defaults=all`、`-R:MaxHeapSize=1g`；
  新增 `emit build report`（打成独立报告包交付，作为后续调参依据）；
  两份参数文件（JDK 25 / JDK 27）逐条同步且保持行序一致。
  `--enable-url-protocols` 属「静默坏掉」风险项，保留但写明退出路径（`TODO.md` N-14）。
- **XML 注释守卫**：`bin/CheckScripts.py` 新增 `check_xml`——注释体禁 `--`、禁嵌套注释
  （本轮两次踩到，且守卫当场抓出技能模板里的嵌套注释）。
- **修复原生镜像「静默跳过」**：`native.skip` 的默认值原先写在模块自身的 `<properties>` 里，
  会**赢过**父 POM 中 profile 的覆盖，导致 `native-image` 被静默跳过——
  构建成功、零产物、零报错、CI 全绿。现改为：默认值唯一来源放聚合 POM 的 `<properties>`，
  同一 POM 的 profile 覆盖为 `false`，CI 再显式传 `-Dnative.skip=false` 兜底，
  并新增「确认 `native-image` 真的执行过」（用 `<name>.build_artifacts.txt` 作执行痕迹）
  与产物缺失时的点名告警。规则写进 `BuildSpec.md`（「构建成功不是验收标准」）与技能。
- **原生镜像管线改为真「可选」**：容错从作业级下沉到**步骤级**
  （作业级 `continue-on-error` 只保证整次运行不变红，作业本身仍显示失败），
  产物自证改为**诊断式**（先打现场、判据只留真不变式、结论进 Job Summary），
  无产物时发布作业既不发布也不失败。修三个具体缺陷：Windows 上 `.cmd` 不能由 bash 直接执行、
  GraalVM 的 JDK 27 尚未发布、无制品时 `exit 1`。实测同轮 linux-x64 与 macos-arm64 的
  原生编译**成功**，说明整条管线是通的。
- **构建契约明确化**：默认构建（不带 profile）只保证「能编译、能打 jar、不报错」；
  工具链 / 平台相关的产物（原生镜像）**只在专用 profile 下**构建，
  且内层开关默认关闭（`native.skip` 默认 `true`，由 `-Pdesktop-native` 置为 `false`）。
  实测默认 `clean package`：反应堆 7 个模块、native 相关日志 0 行、原生模块 `target/` 未被触碰。
  规则写进 `BuildSpec.md` §7 与 `DesktopNativeDesign.md` §2.0。
- **构建产物统一落 `Dist/`**：桌面端便携包与原生镜像包不再输出到仓库根，
  与 CLI 发行包一起放进 `Dist/`（已在 `.gitignore` 里）；
  CLI 的 zip 改由工作流在临时目录打好后移入，避免把正在写入的归档自身收进去。
  规则写进 `BuildSpec.md` §7。

### 修复
- **JVM 模式渲染诊断的 `--add-opens` 目标修正（issue #48）**：`bin/AhaDesktop.bat` /
  `bin/AhaDesktop.sh` 原写 `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED`。
  桌面端以 `--module` 启动，应用是**具名模块**，`ALL-UNNAMED` 不给它任何授权——真机实测
  「字体实现工厂」一项打印 `不可用（InaccessibleObjectException）`，原生 / JVM 对照缺关键一条。
  目标改为 `com.acanx.module.aha.desktop` 后即正常打印 `DWFactory`；手册示例一并更正。
- **渲染诊断的日志定位修正（issue #48）**：真机实测日志落在 `%USERPROFILE%\.aha\Log\AHA.log`
  （由 `Aha.Logging.File=${AHA_HOME:-~/.aha}/Log/AHA.log` 决定），**不是**启动目录——
  采集脚本第一版的候选路径里漏了 `~/.aha/Log`，真机上会报「找不到日志」。
  已把 `~/.aha/Log/AHA.log` 加入候选（并置于最前），手册里也把「配置优先」的查找顺序与
  实测路径改写清楚。**这次是 ACANX 用真机输出反查出文档与脚本的错，值得记一笔。**
- **原生桌面镜像暗色下文字仍是黑色（issue #49 第四轮）**：上一轮（PR #57）后真机反馈「亮色基本解决、
  暗色下很多文字区域仍是黑字」。根因是那份补丁表把文字色写成了 `-fx-text-fill: -fx-aha-text;`
  ——查的是根节点内联样式里定义的自定义 color，而**原生镜像下这种查表不可靠**：值查不到时 CSS 会
  **丢掉整条声明**，文字色回落 modena 默认黑。**亮色下碰巧正确**（亮色本就该是深色字），暗色下暴露。
  修法：拆成 `aha-theme-dark.css` / `aha-theme-light.css` **两份纯字面量**（零变量、零 CSS 函数），
  由 `ThemePaint` 按主题整份装载、切换主题时整份替换；`Palette` 删掉 `-fx-aha-*` 定义；
  另补一层「文字兜底」覆盖 `.label` / `.cell` / `.menu-item > .label` 等全部文字载体，
  并单独处理 **`Text` 节点（走 `-fx-fill`）**。守卫 `PaletteTest` 新增两条：两表声明里不得含
  变量查表与 CSS 函数、两表必须真的不同（暗 `#E4E4E4` / 亮 `#1F1F1F`）。
  详见 `Docs/Troubleshooting/TS-2610-031-Dark-Text-CSS-Patch-Failure.md`。
- **原生桌面镜像字体渲染与 JVM 模式不一致（issue #48）——本轮先做诊断**：真机报原生包的字发虚、
  字形偏细，明显不如 JVM 模式锐利。本轮把「原生包到底走的哪条路」变成可观测的事实：
  ① 实测 `V0.1.1.00055` 的 **Ubuntu 原生包**（本机 WSLg 可跑），确认原生镜像里 JavaFX 的原生库
  加载链路正常，ES2 在本机因无 GPU 回退 SW（预期行为，不能据此推断 Windows）；
  ② 用像素实验**证伪**「软件管线没有 LCD 次像素抗锯齿」——`prism.lcdtext=true/false` 在 SW 管线
  下的产物不同（4375 B / 3025 B，md5 不同），说明 SW 同样支持次像素抗锯齿；
  ③ `AhaDesktopApp.main` 默认打开 `prism.verbose`（JavaFX 会打出实际管线名），`start()` 新增
  `logRenderingDiagnostics()` 一次打全：`prism.*` / `glass.platform` 属性、JavaFX 版本、
  默认字体族 / 名称 / 字号 / 可用字体族数、**字体实现工厂（`PrismFontFactory.getFontFactory()`
  的实现类，反射读取）**、**同一段文字 14px 下的宽 / 高 / 基线（字形度量）**、屏幕
  outputScale / dpi；为此新增 1 条可达性元数据（439 → 440）与 1 条测试守卫（12 → 13）。
  **只加日志、不改渲染行为**——拿到真机两份日志（原生 / JVM）后再定修法。
  诊断代码已先在本地 JVM 模式实跑：`字体实现工厂` 在 Windows 上应为 DirectWrite 的
  `DWFactory`、非 Windows / 无 DirectWrite 时为内置 FreeType 的 `FTFactory`——**两边若不同，
  即说明字形栅格化走了另一条路**，这正是「笔画偏细、发虚」最可能的解释。
  详见 `Docs/Troubleshooting/TS-2610-030-Native-Font-Rendering-Diff.md`。
- **原生桌面镜像渲染诊断的操作手册与收集脚本（issue #48）**：把「真机上各跑一次原生包与
  JVM 模式、把同样的几行日志拿出来对照」落成可照做的材料——新增
  `Docs/Guide/NativeRenderDiagnosticsGuide.md`（步骤、日志位置、判定表、诊断行含义、常见问题）
  与 `Script/Python/CollectRenderDiagnostics.py`（自动定位 `Log/AHA.log`、**只取最后一次启动的片段**、
  生成可直接粘贴的 Markdown 报告）；`bin/AhaDesktop.{bat,sh}` 补上
  `--add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED`，否则 JVM 模式下
  「字体实现工厂」会因非导出包被模块系统拒绝、只打「不可用」，对照就少一条关键证据。
- **原生桌面镜像暗色下控件背景与文字未适配（issue #49）**：切到暗色后，侧边栏搜索框 / 会话列表、
  中部输入框仍是亮色（白）底，侧边栏导航按钮、菜单栏与状态栏文字仍是深色，均不可读。两处成因：
  ① `Palette.theme()` 返回**缓存的静态字段**，与运行期更新的 `FOREGROUND` 等字段在原生镜像下
  不同步（出现「底色已换暗、文字却是亮色主题值」的混合状态）；② 搜索框 / 列表 / 输入框依赖
  JavaFX 默认（modena）背景，只靠根节点的 looked-up color 传不到位。修法：`theme()` 去缓存、
  每次按当前色表现算；`applyTheme` 逐个节点异常隔离（一个失败不中断整屏重刷）；搜索框、输入框、
  会话单元格、菜单栏、中栏滚动区改为显式套主题。守卫 `PaletteTest` 新增一条，
  钉住「切换后样式串立即反映新色表」。详见 `Docs/Troubleshooting/TS-2610-026-Dark-Mode-Control-Colors.md`。
- **原生桌面镜像主题残留的第二轮修复（issue #49）**：上一轮合入后真机仍报「暗色下左栏文字深色、
  亮色下供应商对话框底色深色」。经 build-report 核对（`modena.css` 已随镜像打包）与 JVM 探针
  验证（`Label` / `Button` / `Menu` / `DialogPane` 两套主题下均正确）后确认：问题只在原生镜像
  对 **looked-up color 查表 / `ladder()` 推导**的不可靠上。修法：`Palette.theme()` 显式钉死
  `-fx-text-base-color` 等文字类颜色（不再依赖 `ladder(-fx-base)`）；新增 `Palette.dialogTheme()`
  给对话框显式底色（不再依赖 `.dialog-pane` 的 `-fx-background` 查表），7 个对话框统一改用；
  `applyTheme` 增加主题诊断日志，真机跑一次即可判断「色表没换」还是「节点没刷」。
  经真机截图像素取证（左栏按钮文字 `#000000`、搜索框与输入区纯白 `#FFFFFF`、而靠显式属性的
  区域全部正常）确认：原生镜像下 looked-up color 查表与 `ladder()` 推导不可靠；**据此新增
  `ThemePaint`，把 `-fx-text-fill` / `-fx-background-color` 用属性 API（`Labeled.setTextFill` /
  `Region.setBackground`）再设一遍，彻底绕开 CSS 引擎**（左栏 7 个导航按钮、分组标题、会话单元格、
  搜索框、输入框、状态栏、菜单栏与 7 个对话框全部覆盖）；搜索框 / 输入框再补显式
  `-fx-background-color`，并在 `buildRoot()` 之后幂等重刷一次主题。
  另补回一处漏合并：状态栏版本标签改用 `AppVersion.buildVersion()`。
  `PaletteTest` 新增两条守卫。详见 `Docs/Troubleshooting/TS-2610-028-Theme-Lookup-Table-Failure.md`。
- **原生桌面镜像输入框 / 下拉框 / 按钮 / 滚动条背景发黑（issue #49 第三轮）**：亮色与暗色下，
  文本输入框、「发送」按钮、下拉选择框与会话列表滚动条的背景都是黑的，且与主题切换无关。
  查 `modena.css` 确认根因：这些控件的 `-fx-background-color` 写成 `derive(...)` /
  `linear-gradient(...)`，而原生镜像下 CSS 函数求值不可靠，**CSS 的规则是一个值无效就丢弃
  整条声明**，于是控件根本没有背景（并非「被设成了黑色」）。修法：新增纯字面量的补丁样式表
  `aha-theme.css`，经 `Scene.getStylesheets()` 加载（排在 modena 之后）覆盖之；`Palette` 新增
  `HOVER_BACKGROUND` / `SCROLL_TRACK` / `SCROLL_THUMB` / `ON_ACCENT`，并把 modena 依赖的中间量
  （`-fx-body-color` / `-fx-text-box-border` / `-fx-outer-border` / `-fx-box-border` …）全换成字面量。
  守卫 `PaletteTest` 新增三条：主题样式串与补丁表均不得含 CSS 函数、亮色下控件面色必须为浅色、
  补丁表必须在 classpath 上。详见 `Docs/Troubleshooting/TS-2610-029-Modena-CSS-Function-Failure.md`。
- **原生桌面镜像主题不跟随系统、切主题有残留、暗色标题对比度不足（issue #44）**：原生桌面端能开窗后暴露四类界面问题，本次修掉三类确定性缺陷。
  ① **默认主题恒为暗色**：`SystemTheme.prefersDark()` 在系统配色读取失败或尚未就绪时一律回退暗色；
  改成三级回退（明确配色 → 系统背景色亮度 → 亮色），并新增 `SystemTheme.onColorSchemeChanged`，
  让「跟随系统」在用户切换系统深色 / 浅色皮肤时实时生效。
  ② **切换到亮色后部分组件仍是暗色**：3 处边框硬编码 `#3A3A3A`、1 处背景硬编码 `#1E1E1E`
  改为读 `Palette.BORDER` / `Palette.BASE`；常驻的候选弹层（`CompletionPopup`）与会话右键菜单
  （`ContextMenu`）补上主题重刷（弹层有独立的场景根，不继承主窗口样式）。
  ③ **暗色下标题文字对比度不足**：右栏「本轮」标题与左右折叠按钮此前未登记主题，
  改用默认深色文字；现统一走 `themed(...)` 登记。
  遗留：**原生镜像与 JVM 模式的字体清晰度 / 字体差异**未解决，已另开 **issue #48** 长期跟踪
  （`Docs/TODO.md` 的 `N-24` 只作索引），需真机对比渲染管线后才能定位。
  详见 `Docs/Troubleshooting/TS-2610-025-Theme-Not-Following-System.md`。
- **原生桌面镜像能开窗但控件画不出：Prism 效果 peer 的动态类名未登记（issue #41）**：修完 #35/#37/#39 后原生桌面端首次进到 GUI，
  但渲染到第一个用阴影效果的控件时反复报 `Could not create peer LinearConvolveShadow for renderer
  com.sun.scenario.effect.impl.prism.ps.PPSRenderer`，界面无控件可画。根因与 #35 同源：
  `Renderer.getPeerInstance` 用**动态拼接的类名**反射加载效果 peer
  （`Class.forName(rootPkg + ".impl.prism.ps.PPS" + name + "Peer")`，另含 `prism.Pr*` 与软件回退
  `sw.java.JSW*` / `sw.sse.SSE*`），closed-world 静态分析看不到，而之前只登记了渲染器工厂与 stock shader，
  **未登记 peer 本身**。修法：对 javafx-graphics 25 的 jar 扫 `com/sun/scenario/effect/impl/**/*Peer`，
  过滤 abstract 后把 **99 个具体 peer** 全部登记进 `reachability-metadata.json`（340 → 439 条）。
  守卫 `NativeImageMetadataTest` 11 → 12 条（已反向验证），产物自证第 ⑧ 条补 peer 检查。
  详见 `Docs/Troubleshooting/TS-2610-023-Prism-Effect-Peer-Missing.md`。
- **原生桌面镜像 Windows 启动即崩：平台子类的 JNI 成员查找未登记（issue #39）**：修完 #37（JNI 可达**类**）后，
  真机在 `WinWindow.<clinit>` 报 `NoSuchMethodError:
  com.sun.glass.ui.win.WinWindow.notifyMoving(IIIIFFIIIIIII)[I`。根因：`WinWindow._initIDs` 用
  `GetMethodID(cls, …)` 查的是「Java 传入的 `jclass`」——即平台子类**自己声明**的方法；
  而 #37 的 `FindClass` 扫描只会看到 native 里写成字面量的类名，平台子类从不经 `FindClass`，
  因此从未入册（只在册的基类 `Window` 与它继承的方法能查到）。修法：改扫 openjfx 三平台
  native 源码里所有 `Get*ID` 的**目标类**，把 Windows / macOS 的平台实现类逐类补进
  `jni-config.json`（62 → 85 条），并顺带补上 `Class.forName` 现查的 `WinDnDClipboard` / `EventLoop`。
  守卫 `NativeImageMetadataTest` 10 → 11 条（已反向验证），产物自证第 ⑧b 条补 4 项。
  详见 `Docs/Troubleshooting/TS-2610-022-Win-Window-JNI-Member-Missing.md`。
- **原生桌面镜像启动即 segfault：JNI 可达类未注册（issue #37）**：修完 #35（JavaFX 启动链路反射）后，
  真机在 Glass 初始化处报 `NoClassDefFoundError: java/lang/Runnable`，随后段错误。
  根因是 native-image 只允许「JNI accessible」的类被 `FindClass` 查到——反射元数据解决「类可达」，
  不解决「JNI 可达」。修法：新增 `jni-config.json`（62 个类，来自对 openjfx 三平台 native 源码
  506 个文件里所有 `FindClass` 的静态扫描），并在单测与产物自证里加守卫。
  详见 `Docs/Troubleshooting/TS-2610-021-JNI-Find-Class-Segfault.md`。
- **原生镜像管线两个跨平台缺陷（run 37744912968）**：
  ① macOS 腿的产物自证里 `$bin（` 被 bash 当成变量名 → `set -u` 下
  `bin（: unbound variable` 退出，步骤 outputs 丢失，下游改名/上传被静默跳过——
  **macos 镜像包因此没传上发布页**。修法：变量后跟全角字符一律写 `${VAR}`；
  命中产物后**立刻**写 `produced=true`，并加 `trap 'exit 0' EXIT` 兜底。
  ② Windows 腿的工具链自证里 `PLATFORM` 与 runner 自带的 VS 环境变量冲突 →
  `unbound variable` 退出。修法：注入名改 `LEG_PLATFORM` / `LEG_LABEL` / `LEG_VERSION`，
  一律用 `${VAR:-}` 读取，只做记录的命令失败不判死。
  顺带修：魔法数只比前 2 字节（原先 Windows 期望 `4d5a`、实际 `4d5a9000`，永远不匹配）、
  去掉 `find -maxdepth`（macOS BSD find 不支持），并把 `Release.yml` 同写法一并修正。
- **跨平台 shell 守卫**：`bin/CheckScripts.py` 新增 YAML 检查——`$VAR` 后紧跟非 ASCII
  且未用 `${}` 就报错（已反向验证）；同一条规则不再靠人盯。
- **原生镜像桌面端启动链路反射 / JNI 缺失（issue #35）**：修完 #26 后，二进制下一处报
  `ClassNotFoundException: com.sun.javafx.tk.quantum.QuantumToolkit`——JavaFX 的工具包、
  Glass 平台工厂、Prism 渲染管线都用 `Class.forName` + `getDeclaredConstructor().newInstance()`
  这类反射加载，native-image 的 closed-world 看不到。经对 4878 个 JavaFX 类逐个反编译审计后，
  把反射与 JNI 清单一次性补进 `reachability-metadata.json`（后经 tracing agent 采集补齐至 **340 条**）：工具包与日志 / 反射辅助类、
  三平台 Glass 工厂、四条 Prism 管线、效果渲染器、ShaderSource 与**全部 212 个** stock shader 加载器、
  Glass 原生回调（`jniAccessible`）、图片解码、字体（DirectWrite / CoreText / FreeType）；
  审计同时发现 **Jackson 3 不随附 native-image 元数据**，而配置与会话记录靠它反射读写，
  因此一并注册了 13 个配置记录与 `TaskRequest` / `TaskResult` / `ToolCall`。
  另把 `-H:IncludeResources` 补上 D3D 的 `.obj` 与 ES2 的 `.frag` / `.vert` 着色器资源
  （漏掉不会构建失败，而是首次绘制时静默坏掉）。
  `NativeImageMetadataTest` 扩到 7 条断言（已反向验证），DesktopNative 产物自证第 ⑧ 条改为
  逐类检查启动链路。**诚实说明**：这是手工推导的「已知缺口已闭」，不等于「证明完整」——
  真机逐功能验证仍待 `N-05` / `N-16`，抄底方案（tracing agent）登记为 `N-17`。
- **原生镜像桌面端启动即崩（issue #26）**：`aha-desktop-native.exe` 一启动就报
  `ClassNotFoundException: com.acanx.module.aha.desktop.AhaDesktopApp`——JavaFX 入口有两处反射：
  `Application.launch(String...)` 用 `Class.forName` 加载主类，`LauncherImpl` 又用
  `getConstructor().newInstance()` 实例化它，而 native-image 的 closed-world 看不到。
  现补上 `reachability-metadata.json` 的主类构造器注册，`main` 改为显式
  `launch(AhaDesktopApp.class, args)`；新增 `NativeImageMetadataTest` 在 Build / Gate 阶段守住
  注册，DesktopNative 产物自证再查一次构建产物里的元数据。
- **原生镜像缺独立构建报告（issue #29）**：`dist-native.xml` 原先排除
  `<可执行名>-build-report.html`，而文档已把它记为「已随包交付」，两边对不上——
  发布页上既看不到包内报告，也没有独立的报告发布物。现定案：报告**不进镜像包**，
  改由工作流打成独立发布包
  `AHA-Desktop-Native-Report-<版本>-<系统>-<架构>-jdk<JDK>.zip`（版本、系统、
  架构、JDK 轴全在文件名里，与镜像包一一对应），并在产物自证里新增
  「报告包是否真的生成」的回归守卫——「workdir 里有报告」不等于「发布物里有报告」。
- **`.gitignore` 静默吃掉源码**：不带前导斜杠的 `Log/` 在任意层级匹配，且在 Windows / macOS
  大小写不敏感，于是 `aha-desktop/.../desktop/log/` 的 8 个源文件从未进入版本控制——
  git 不报错、`git add -A` 静默跳过、`git status` 显示干净、本地测试全绿，
  只有 CI 报 `cannot find symbol`。运行期目录已锚定到仓库根（`/Data/` `/Key/` `/Log/` `/Model.yml`）
- **残留的合并冲突标记**：`.github/workflows/CodeQL.yml`、`Docs/DevSpec/BuildSpec.md`、
  `Docs/TODO.md` 里共 11 处标记已入库，其中 `CodeQL.yml` 因此一直是**无效工作流**；
  已逐处解析（CodeQL 行动升到 v4，文档版本取新）
- `InputHistory.next()` 复位顺序错误会丢掉用户正在输入的草稿
- `FileMentions.insert()` 在光标位于片段之后时会替换错位置
- `LogCapture` 卸载后重新安装会静默失效（判重条件问错了对象）
- **主题应用顺序**：主题原来在界面建好之后才应用，只能靠逐个重刷已建节点；一旦漏掉某个控件
  就会出现「日志说生效亮色、界面还是暗的」。改为启动时**先定色表再建界面**，
  并把生效主题与实际色值写进日志自证
- **自动打 tag**：`Build.yml` 新增 `tag` 作业——`dev` → `main` 的 PR 合并、且构建成功后，
  按根 `pom.xml` 的 `<version>` 创建 `V<版本号>` 附注 tag 并推送（同名已存在则跳过）；
  版本号只从 POM 读，工作流与文档不复制。注意：`GITHUB_TOKEN` 推的 tag 不会触发下游工作流，
  故 `Release.yml` 需手工触发或改用 PAT（见 [ReleaseProcess.md](Docs/DevSpec/ReleaseProcess.md) §4.2）
- **桌面端按平台出包**：新增 `aha-desktop/src/assembly/dist-desktop.xml`，产出便携包
  `aha-desktop-<版本>-<系统>-<架构>.zip`（`bin/` 启动脚本 + `lib/` 本项目模块与本平台 OpenJFX
  原生库），文件名由构建期真实解析结果决定；`Release.yml` 新增 `desktop` 作业按
  `ubuntu` / `windows` / `macos` 矩阵出包，由产物名 + 依赖树**双向自证**平台分类器后上传到
  release 页面。桌面端启动脚本 `bin/AhaDesktop.{sh,bat}` 随包分发；`aha-desktop` 另声明
  `aha-tool`，桌面端由此具备内置工具
- **桌面端三栏骨架**：`DesktopShell` 搭出菜单栏 + 左 220 / 中弹性 / 右 280 + 底部 24px 状态栏；
  左右两栏可折叠，菜单（视图）、快捷键（`Ctrl+B` / `Ctrl+J`）与栏边**常驻窄条按钮**三条路径等价
  ——折叠后窄条仍在，鼠标用户随时能把栏展回来；折叠状态抽成不依赖 JavaFX 的 `FoldState`，
  使「折叠→展开」的往返在无图形环境下也能被测试钉住；输入区支持 Enter 发送 /
  Shift+Enter 换行，发送只落到本地消息流并明确提示「尚未接入 Agent」
- **桌面端窗口骨架与线程桥接契约**（0.2 的第一步）：`AhaDesktopApp` 改为 `Application` 子类，
  开窗并显示版本号；新增 `desktop/fx` 桥接契约（`FxDispatcher` / `PlatformFxDispatcher` /
  `FxBridge`），把高频后台回调合并成每帧至多一次界面更新；`FxThreadContractTest` 扫描主源码
  钉住「只有 `PlatformFxDispatcher` 可触碰 JavaFX 线程 API」；窗口冒烟测试默认跳过
  （需图形环境），用 `-Daha.ui.tests=true` 显式开启
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

- **CodeQL 工作流上线实测与升级**：`init`（`build-mode: manual`）与 `compile` 三步运行全过，
  失败集中在 `analyze`（上传结果）；已把 `github/codeql-action` 升到 `v4`（v3 将于 2026-12 弃用），
  并注明 `build-mode` 那条提示为良性。`main` 规则集要求 CodeQL，而默认设置与高级设置互斥——
  处置与验收标准见 `TODO.md` `G-04`

- **代码扫描（CodeQL）**：新增 `.github/workflows/CodeQL.yml`——`main` 的规则集要求 CodeQL 结果，
  而仓库此前没有任何 code scanning 配置，PR #7 因此永久停在「Waiting for Code Scanning results」。
  工作流显式覆盖 `pull_request → main`（默认设置只扫默认分支，覆盖不到），不参与必需检查
- **文档提交改落在 `dev`**：规则集整改规格与 `F-12` 复发记录两条文档提交现位于 `dev`
  （`abd5d85` / `ca80c5c`），`dependa` 已复位到 `origin/dependa`；复位后的实测代价
  （首次 `dependa ← dev` 会在 6 个文档文件上冲突及解法）已记入 `TODO.md` `G-01`
- **补回 `dev` 的血缘**：PR #8 以 squash 合入，使 `dev` 拿到内容却没拿到分支历史——
  与 `F-12` 同一形态并在同日复发。已在 `dependa` 上用 `-s ours` 接回（树不变、零内容改动），
  并按 `ReleaseProcess.md` §4.1 立下「被误用 squash 后必须立刻接回血缘」；
  更根本的预防（关闭 squash/rebase 合并）登记为 `TODO.md` `G-05`
- **分支规则集整改规格**：`main` / `dev` 的规则集里存在三条对「单人 + 机器」无法满足的要求
  （要求他人批准、要求 CodeQL 结果却未配置扫描、要求覆盖率数据却无上传），会把 PR 永久锁死；
  整改规格与验收标准已写入 `TODO.md` `G-02` / `G-04`，规范写入 `BuildSpec.md` §8.1
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
- **开发日志**：新增 `Docs/Troubleshooting/TS-2610-002-Coverage-Gate-Invisible.md`，记录门禁静默这一问题的核实方法
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

- 发行包（`mvnw clean package` 产出 `Dist/`）：`maven-assembly-plugin` 将 `aha-cli` 自身、
  全部模块与第三方依赖收集到 `lib/`，并附带 `bin/Aha.sh` / `bin/Aha.bat` 启动脚本；
  分发方式为 JPMS 模块路径目录，与启动脚本的 `--module-path lib` 一致
- **跨平台启动脚本**：`Aha.sh` 支持 `JAVA_HOME`、校验 `lib/` 存在并给出可读错误提示；
  `Aha.bat` 为**纯 ASCII + CRLF**（CMD 按 ANSI 代码页解析批处理），提示信息用英文；
  `bin/CheckScripts.py` 在 CI 中守住该约束
- 启动脚本传递 `--enable-native-access`，消除 JDK 25 下 `sqlite-jdbc` 与 `jline`
  的受限方法警告
- `Release.yml` 发布完整的 `dist.zip`
- 父 POM 补充发布元数据：`<url>`、`<licenses>`（Apache-2.0）、`<developers>`、`<scm>`
- `mvn clean` 一并清空 `Dist/` 中的构建产物（保留目录）：assembly 只覆盖同名文件，
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

[0.1.0]: https://github.com/ACANX/AHA/releases/tag/V0.1.0

### 变更
- **供应商配置更好用**：对话框宽度翻倍；列表用**绿灯**标出当前启用的供应商并附其模型
  （状态行复述「● 已启用：<供应商> <模型> 密钥 <打码>」）；打开时默认选中当前启用项；
  新增供应商支持「按预设新建」（六家已知供应商一键填好适配器 / 基础地址 / 默认模型），
  模型字段是可编辑下拉（候选取自 `ModelDefault.yml` 的真实模型名，也允许自己填）
- **桌面端可用了（最小可用版）**：接入 Agent，能真的对话——流式正文、工具卡片（类别配色 + 结果行）、
  错误展示、用量回填、`Esc` 中断；左栏导航可用（新建会话 / 供应商 / 工具）；
  输入框启动即获得焦点；供应商配置改为**可查看与修改的表单**（适配器 / 基础地址 / 模型 /
  API Key（打码，可显示）/ 超时 / 重试 + 保存 / 设为默认 / 新增 / 删除，写回 `Model.yml`）。
  对话内核与表单校验都抽成不依赖 JavaFX 的类（`ChatController` / `ChatView` / `ToolSummary` /
  `DesktopToolApprover` / `ProviderForm`），因此整条链路在无图形环境下有 49 例测试覆盖
- **桌面端窗口图标与标志**：把 `Logo.svg` 栅格化为 PNG 入库（新增 `bin/GenLogoPng.py`），
  用作窗口 / 任务栏图标与空会话展示；生成器把 SVG 根元素宽高写成目标像素数、
  开一个更高的窗口，再自己解码 PNG 按 alpha 裁掉透明边并**自检包围盒**
  （第一版只验「是正方形、够大」，于是「只渲染出四分之一 / 只保留上半」的裁切图全数通过，
  到真机启动才被发现）
- **窗口尺寸按 DPI 缩放计算**：`Screen` 报的是物理像素而场景尺寸是逻辑像素，
  之前在高 DPI（本机 150%）下按 800 逻辑像素开窗会变成 1200 物理像素，把输入区与底部状态栏
  顶出屏幕；同时给消息区 `setMinHeight(0)`，避免 `ScrollPane` 把输入区挤出窗口
- **桌面端正式读取配置**：新增 `aha-core` 的启动引导 `AhaBootstrap`（读项目 `./Aha.yaml`
  → 装配日志 → 装密钥库回退源），CLI 与桌面端共用；桌面端窗口新增一行配置摘要
  「配置：<路径> · 日志级别：<级别>」。此前桌面端固定用默认日志配置，
  `Aha.Logging.*` 在桌面端完全不生效
- **版本号与日志装配下移到公共模块**：`version.properties` 与 `AppVersion` 迁到 `aha-common`
  （picocli 适配留在 `aha-cli` 的 `CliVersionProvider`，避免把 picocli 带进零依赖模块）；
  `LoggingSetup` 迁到 `aha-core`（该模块的 `log4j-core` 改 `compile`）。桌面端因此能取到版本号、
  配置日志，且无需依赖 `aha-cli`
- **release 资产改名**：CLI 发行包由 `aha-<tag>-dist.zip` 改为 `aha-<版本>-cli.zip`
  （版本从产物名读，不再用含 `V` 前缀的 tag 名），与桌面端的
  `aha-desktop-<版本>-<系统>-<架构>.zip` 命名保持一致

### 修复
- **主配置不可用时不再让后续流程抛异常**：启动引导会退回内置默认并记一条警告；
  此前 CLI 会把 `null` 传给后续流程，`CliContext` 二次读取配置时会抛错
- **版本号清单不完整（静默错版本）**：`ReleaseProcess.md` 原文称「版本号只需改根 `pom.xml`」，
  实测只改根 POM 时六个子模块仍按 `<parent>` 声明的旧版本解析——反应堆显示
  `Building AHA-Common 0.1.0`、产物名 `aha-common-0.1.0.jar`、`aha --version` 仍报旧版本，
  而构建**不报错**。清单更正为 8 处（根 POM + 六个子 POM 的 `<parent>` + `AppVersion.FALLBACK`）
- **CHANGELOG 链接 404**：`[0.1.0]` 指向 `releases/tag/V0.1.0`，而实际 tag 是 `0.1.0`
- **发布日**：`[0.1.0]` 由 `2026-10-06` 更正为实际发布日 `2026-10-07`（发布提交 `9138847`）

## [0.1.0] - 2026-10-07

首个可用版本：Core + CLI 可运行。

### 新增
