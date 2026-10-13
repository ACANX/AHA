# TS-2610-009 · 桌面端六功能开发——测试与真机抓到的 5 个真 bug

> 日期：2026-10-08
> 作者：@ACANX
> 关联 PR：`feat/version-0.1.1`（0.2 桌面端：卡片 / 会话列表 / 日志 / 输入 / 授权 / 主题）
> 关联记录：`Docs/Design/DesktopDesign.md` §11、`Docs/Dbsx.txt`、`TODO.md` `D-12`

## 1. 背景

按用户指定的顺序（工具卡片 → 会话列表 → 日志面板 → 输入区增强 → 授权弹窗 → 主题与设置）
把桌面端从「能聊天」推到「能长期用」。六个功能都按项目既定套路落地：
**可判定的部分抽成不引用 JavaFX 的纯类并配单测**，薄壳交给冒烟测试与真机走查。

过程中被测试与真机连着抓到 **5 个真 bug**，另有 1 次**把坏状态推上远端**的事故值得单记。

## 2. 排障过程与修复链

### 2.1 我在实现里写错、被测试抓住的 5 处

| # | 缺陷 | 后果 | 测试怎么抓到的 |
|---|---|---|---|
| 1 | `LogCapture.install()` 用「配置里有没有这个 appender」判重 | **卸载后再安装静默失效**（面板再也收不到日志） | `reinstallAfterUninstallCapturesAgain`（装 → 卸 → 再装 → 写一条 → 断言进缓冲） |
| 2 | `InputHistory.next()` 先 `reset()` 再返回草稿 | 从最早一条往下翻**丢掉用户正在打的字** | `previousWalksBackAndKeepsTheDraft` 断言回到草稿内容 |
| 3 | `FileMentions.insert()` 用 `caret - token.length()` 定位片段起点 | 光标在片段**之后**时替换错位置 | `insertReplacesTheFragmentAndMovesCaret` 用真实光标位 |
| 4 | `ApprovalText.permissionLabel` 的 switch 漏了 `NETWORK` | 编译失败（枚举 5 个值只写了 4 个） | 编译器（`switch` 表达式必须全覆盖） |
| 5 | `ToolCard.resultLine` 的耗时取整我按 HALF_EVEN 想 | 期望 0.2s 实际 0.3s（Java `Formatter` 是 HALF_UP） | `secondsRoundsHalfUpToOneDecimal` |

教训很直白：**这五处都不是「写不出来」，而是「想当然」**——判重条件、清理顺序、坐标换算、
枚举分支、取整规则。它们的共同点是有明确的真值，因此都能用纯逻辑测试钉住；
这也是「把可判定的部分从视图里抽出来」这条策略真正的价值。

### 2.2 真机抓到的 2 处

- **搜索框横向溢出**：`TextField` 的 `maxWidth` 默认按内容算，于是它比左栏还宽、
  盖到中栏上（真机截图可见）。修：显式放开 `maxWidth` 让 `VBox` 把它收进栏内，
  并在冒烟测试里加「控件宽度不得超过左栏」的断言。
- **对话框高过屏幕**（上一轮遗留，本轮延续）：已在供应商对话框用「限高 + 滚动」解决。

### 2.3 主题「生效了却没换」：顺序问题

真机上日志写着「界面设置：主题 跟随系统（生效 亮色）」，界面却仍是暗色。

根因不是配色表，而是**顺序**：主题在界面建好之后才应用，而颜色是内联在样式串里的，
只能靠 `themed` 登记的重刷动作逐个覆盖——重刷路径一旦漏掉某个控件，就会留下「日志说亮色、
界面还暗着」。修法是把顺序倒过来：**先 `Palette.setTheme(...)`，再 `new DesktopShell(...)`**，
让新节点天生就是对的，重刷只作为切换主题时的补充。同时把生效主题与实际色值
（前景 / 底色）打进日志，下次不必靠截图猜。

**教训**：初始化顺序也是契约。能「先定好再建」的，就不要「建好再改」——
后者要求每条重刷路径都正确，而前者只要求一次正确。

### 2.4 事故：把坏状态推上远端，CI 编译失败

**现象**：用户在浏览器里看到 `build (ubuntu-latest, system)` 红：
`[ERROR] symbol: class LogLevel … location: class …view.LogPanel`。

**根因**：`.gitignore` 里写着**不带前导斜杠**的 `Log/`，本意是「AHA_HOME 未设置时落在
仓库根目录的运行期日志目录」。不带斜杠的模式在**任意层级**匹配，而且在 Windows / macOS
（文件系统大小写不敏感）上 `Log/` 会命中 `log/` ——于是它吃掉了
`aha-desktop/src/main/java/.../desktop/log/`，**8 个源文件一个都没进版本控制**：

- git 不报错；
- `git add -A` **静默跳过**被忽略的文件；
- `git status` 显示「干净」（被忽略的文件根本不出现在里面）；
- 本地测试**照样全绿**——文件在磁盘上，编译与测试都正常；
- 只有 CI 会告诉你 `cannot find symbol`。

也就是说：我前几次汇报的「工作区干净 ✓」是被这个假象骗了，而且**连续几轮都没发现**。

**修复**：
1. `.gitignore` 把运行期目录锚定到仓库根：`/Data/`、`/Key/`、`/Log/`、`/Model.yml`，
   并把这段坑写进注释；顺带把 `data/`、`logs/` 也锚定。
2. 补回 8 个漏掉的文件。
3. **加守卫**：`bin/CheckScripts.py` 新增「源码文件不得被 .gitignore 吃掉」检查——
   直接问 git（`ls-files --others --ignored --exclude-standard`），凡是落在 `src/` 下
   或本身是 `.java` 的忽略文件一律失败。该检查接在 `Gate.yml` 既有步骤里，不必改 CI 配置。
   反向验证过：新增一个未跟踪的 `.java` 并让 `.gitignore` 命中它 → 检查 `exit=1` 并点名文件；
   清理后恢复全绿。

**教训（本轮最重要）**：`git add -A` 成功 ≠ 文件进了库。
凡新增文件，提交前必须核对 `git ls-files`，并且**不能**拿 `git status` 的「干净」当证据
（它对被忽略的文件一无所知）。这条已写进 `CommitMessageSpec.md` 的「提交前自证」。

## 3. 最终验证结果

| 项 | 结果 |
|---|---|
| 本地快检查 | `./mvnw -pl aha-desktop -am test -Djacoco.skip=true` → **BUILD SUCCESS**；core 217 例、桌面端 147 例（1 例窗口冒烟默认跳过） |
| 脚本与仓库检查 | `python3 bin/CheckScripts.py` → ✅（13 个脚本 + 「源码未被 .gitignore 吃掉」） |
| 工具卡片 | 默认折叠；展开显示参数与带行号输出（前 200 行，标题写明「共 412 行」）；复制 / 全部；失败卡片红边、正文摊开、首行给重试与改参数入口 |
| 会话列表 | 真机列出 SQLite 里真实的历史会话（条数 + 相对时间），当前会话绿点加粗；点选即回放历史（真机实测「已切换到会话 e35f0334，回放 1 条消息」） |
| 日志面板 | 端到端自证测试：装 appender → 写日志 → 断言进缓冲；级别过滤在读时生效（调回 DEBUG 仍能看到之前的 DEBUG） |
| 输入区 | `/` 命令面板、`@` 文件候选、↑↓ 历史（不丢草稿）、Tab/Enter 接受候选；`/help` 等命令**不发给模型** |
| 授权弹窗 | 权限说明 + 完整参数 + 「本会话内始终允许 WRITE」+ 默认焦点在拒绝 + Esc 即拒绝；工具菜单可一键撤销本会话全部授权 |
| 主题 | 暗 / 亮 / 跟随系统；换主题会把**已建节点**一起重刷（冒烟测试断言了这一点，而不是只看新控件） |

## 4. 关键教训

1. **`git add -A` 会静默跳过被忽略的文件**，`git status` 也不显示它们。
   新增文件后必须用 `git ls-files` 核对；这条已经用脚本守卫住。
2. **「本地全绿」不等于「提交完整」**：本地有文件、远端没有，这个差异只有 CI 会暴露。
   推送前的自证应当包含「产物视角」的检查（文件是否入库、包是否含依赖…）。
3. **判重条件要问对人**：「配置里有没有这个 appender」与「根记录器上挂没挂这个 appender」
   是两件事——卸载只摘引用，于是判重条件错的那一版在「卸载后重装」时静默失效。
4. **清理顺序**：先取值再复位。`next()` 里 `reset()` 与 `return draft` 顺序反了就会丢数据。
5. **坐标换算要按真实光标位**：`caret - token.length()` 只在光标正好贴住片段末尾时成立。
6. **枚举分支必须写完**：`switch` 表达式在 `ToolPermission` 增加 `NETWORK` 后立刻编译失败，
   这是好事——比运行到那一条才发现好。
7. **测试替身也要真删**：`InMemoryMemoryStore.deleteSession` 一开始清了自己没有的字段，
   被编译器和断言一起挡住。替身行为与真实实现不一致，测试就在骗人。
8. **能力进了目录才算数**：`/` 命令面板只登记真的能执行的命令；做不到的先不进面板，
   也不在菜单里放「待接入」的占位项（本项目前面已经吃过这个亏）。

## 5. 涉及文件清单

**内核（`aha-common` / `aha-core`）**

- `aha-common/.../model/SessionSummary.java`（会话摘要；标题兜底与短 ID）
- `aha-core/.../memory/MemoryStore.java`（`listSessions` / `updateSessionTitle` / `deleteSession`）
- `aha-core/.../memory/SqliteMemoryStore.java`（`session.title` 列迁移 + 一条 SQL 取全的列表查询）
- `aha-core/.../service/SessionManager.java`（`list` / `rename` / `delete`）
- `aha-core/.../service/AgentService.java`、`LocalAgentService.java`（会话列表 / 历史 / 改名 / 删除）

**桌面端（`aha-desktop`）**

- `chat/ToolCard.java`（卡片文字、截断、耗时、重试文案；取代原 `ToolSummary`）
- `chat/SessionList.java`、`chat/SessionExport.java`（列表口径 / Markdown 与 JSON 导出）
- `chat/SlashCommands.java`、`chat/FileMentions.java`、`chat/InputHistory.java`（输入区三块纯逻辑）
- `chat/ApprovalText.java`（授权弹窗的说辞）、`chat/DesktopToolApprover.java`（询问可见已放行范围 + 撤销）
- `log/LogLevel.java`、`log/LogLine.java`、`log/LogBuffer.java`、`log/LogCapture.java`
- `view/LogPanel.java`、`view/SettingsDialog.java`、`view/DesktopSettings.java`
- `view/Theme.java`、`view/SystemTheme.java`、`view/Palette.java`（两套色表 + 主题样式）
- `view/CompletionPopup.java`、`view/ApprovalDialog.java`、`view/OutputDialog.java`、`view/Clipboards.java`
- `view/DesktopShell.java`（卡片 / 会话列表 / 日志入口 / 输入增强 / 主题切换 / 设置入口；
  29 处 `setStyle` 改为可重刷的 `themed(...)`）
- `AhaDesktopApp.java`（全套接线：会话、导出、日志捕获、设置、主题循环、授权弹窗）
- `module-info.java`（新增 `requires org.apache.logging.log4j(.core)`）

**仓库与脚本**

- `.gitignore`（运行期目录锚定仓库根；带注释说明这次的坑）
- `bin/CheckScripts.py`（新增「源码文件不得被 .gitignore 吃掉」检查）
- `Docs/DevSpec/CommitMessageSpec.md`（新增「提交前自证」）
- `Docs/Design/DesktopDesign.md` §11、`Docs/Design/GUIDesign.md` §11（实现现状）
- `Docs/Dbsx.txt`（六项待办标记完成）、`Docs/TODO.md`、`CHANGELOG.md`

**测试（新增 / 扩充）**

- `ToolCardTest`(14)、`SessionListTest`(6)、`SessionExportTest`(7)、`SlashCommandsTest`(5)、
  `FileMentionsTest`(6)、`InputHistoryTest`(5)、`ApprovalTextTest`(7)、
  `LogLevelTest`(4)、`LogLineTest`(4)、`LogBufferTest`(6)、`LogCaptureTest`(4)、
  `ThemeTest`(4)、`DesktopSettingsTest`(7)、`SessionSummaryTest`(4)
- 扩充：`ChatControllerTest`（+6）、`PaletteTest`（6）、`SqliteMemoryStoreTest`（+5）、
  `SessionManagerTest`（+1）、`DesktopToolApproverTest`（+2）、`AhaDesktopSmokeTest`（+6 段真实控件断言）

---

<!-- 以下四节按 issue #128「选项 C：补齐四节必备项」补入（2026-10-13）。
     正文其余部分保持原样；四节不带序号，以免与本文既有的「1. …」编号冲突。 -->

## 结论分层

### 已观测事实

- **测试抓住 5 处实现缺陷**（详见下表），**真机抓到 2 处**：
  搜索框横向溢出（`TextField` 的 `maxWidth` 默认按内容算，比左栏还宽、盖到中栏，真机截图可见）；
  对话框高过屏幕（上一轮遗留）。
- **主题「生效了却没换」**：真机日志写着「界面设置：主题 跟随系统（生效 亮色）」，界面却仍是暗色。
- **把坏状态推上远端的事故**：远端 `build (ubuntu-latest, system)` 红，
  `[ERROR] symbol: class LogLevel … location: class …view.LogPanel`。
  根因是 `.gitignore` 里**不带前导斜杠**的 `Log/`：它在**任意层级**匹配，且在 Windows / macOS
  （文件系统大小写不敏感）上会命中 `log/`，于是吃掉了
  `aha-desktop/src/main/java/.../desktop/log/`——**8 个源文件一个都没进版本控制**。
  症状链：git 不报错 → `git add -A` **静默跳过** → `git status` 显示「干净」→ 本地测试**照样全绿**
  → **只有 CI** 报 `cannot find symbol`。
- 修复与**反向验证**：`.gitignore` 把运行期目录锚定到仓库根（`/Data/`、`/Key/`、`/Log/`、`/Model.yml`，
  并锚定 `data/`、`logs/`）＋ 补回 8 个文件 ＋ `bin/CheckScripts.py` 新增「源码文件不得被 .gitignore 吃掉」
  （直接问 git：`ls-files --others --ignored --exclude-standard`）。反向验证：新增一个未跟踪的 `.java`
  并让 `.gitignore` 命中它 → 检查 `exit=1` 并**点名文件**；清理后恢复全绿。
- 本地快检查：`-pl aha-desktop -am test -Djacoco.skip=true` → **BUILD SUCCESS**；
  core **217 例**、桌面端 **147 例**（1 例窗口冒烟默认跳过）。
- 脚本与仓库检查：`python3 bin/CheckScripts.py` → ✅（13 个脚本 + 新增的那条检查）。

### 机制 / 行为模型

- **`git add -A` 会静默跳过被忽略的文件，`git status` 也不显示它们**
  ⇒ 「工作区干净」**不能**当作「文件已入库」的证据。依据：8 个源文件在本地存在、
  本地测试全绿，而远端编译失败。
- **不带前导斜杠的 `.gitignore` 模式在任意层级匹配**；在大小写不敏感的文件系统上
  （Windows / macOS）`Log/` 还会命中 `log/`。
- **主题要「先定好再建」，不要「建好再改」**：颜色内联在样式串里时，后者要求**每条重刷路径都正确**
  （漏一个控件就留下「日志说亮色、界面还暗着」），而前者只要求一次正确。
  ⇒ **初始化顺序也是契约**。
- **「本地全绿」不等于「提交完整」**：本地有文件、远端没有，这个差异**只有 CI 会暴露**，
  所以推送前的自证应当包含**产物视角**的检查（文件是否入库、包是否含依赖…）。
- **判重条件要问对人**：「配置里有没有这个 appender」与「根记录器上挂没挂这个 appender」是两件事
  ——卸载只摘引用，于是判重条件错的那一版在「卸载后重装」时静默失效。
- **清理顺序：先取值再复位。**
- 这 5 处缺陷的共性是**「想当然」而非「写不出来」**：判重条件 / 清理顺序 / 坐标换算 / 枚举分支 /
  取整规则，都**有明确的真值**，因此都能用纯逻辑测试钉住——这正是「把可判定的部分从视图里抽出来」
  这条策略的真正价值。

### 未定位 / 未复现

- 「连续几轮都没发现」这一假象**持续了几轮未量化**：档案只说「前几次汇报的『工作区干净 ✓』
  是被这个假象骗了」。
- 真机结论（搜索框溢出、主题实际观感、会话回放提示）在**本机 WSL 无 GTK** 的环境里**无法复现**。
- 守卫只覆盖「`src/` 下或 `.java` 的忽略文件」：**资源文件**（`.fxml` / `.css` / `.png`）
  被 `.gitignore` 吃掉**仍不会被发现**（见未决问题 Q1）。

## 观测数据

| 数据 | 来源 / 命令 | 原始输出或取值 |
| --- | --- | --- |
| 缺陷 1：卸载后重装失效 | `reinstallAfterUninstallCapturesAgain`（装 → 卸 → 再装 → 写一条 → 断言进缓冲） | 判重条件用「配置里有没有这个 appender」→ 静默失效 |
| 缺陷 2：翻历史丢草稿 | `previousWalksBackAndKeepsTheDraft` | `next()` 里 `reset()` 早于 `return draft` → 丢数据 |
| 缺陷 3：插入位置错 | `insertReplacesTheFragmentAndMovesCaret`（真实光标位） | `caret - token.length()` 只在光标贴住片段末尾时成立 |
| 缺陷 4：枚举漏分支 | 编译器 | `switch` 表达式未覆盖 `NETWORK` → **编译失败**（5 个值只写了 4 个） |
| 缺陷 5：取整规则 | `secondsRoundsHalfUpToOneDecimal` | 期望 0.2s、实际 **0.3s**——Java `Formatter` 是 **HALF_UP**，不是 HALF_EVEN |
| 真机缺陷 1 | 真机截图 | `TextField` 的 `maxWidth` 默认按内容算，比左栏还宽、盖到中栏 |
| 真机缺陷 2 | 真机走查 | 对话框高过屏幕（上一轮遗留，本轮在供应商对话框用「限高 + 滚动」解决） |
| 主题不一致的日志 | 真机日志 | `界面设置：主题 跟随系统（生效 亮色）`，而界面仍为暗色 |
| **事故：缺席的 8 个源文件** | `.gitignore` + `git ls-files` | `Log/`（无前导斜杠）吞掉 `aha-desktop/src/main/java/.../desktop/log/` 下 **8 个文件** |
| 症状链（逐环） | 依次观察 | git 不报错 → `git add -A` 静默跳过 → `git status` 「干净」→ 本地测试全绿 → CI `cannot find symbol` |
| 守卫的反向验证 | 造一个被忽略的未跟踪 `.java` 后跑 `CheckScripts.py` | `exit=1` 并**点名文件**；清理后恢复全绿 |
| 依赖模块测试 | `-pl aha-desktop -am test -Djacoco.skip=true` | BUILD SUCCESS；core **217 例**、桌面端 **147 例**（1 例跳过） |
| 脚本与仓库检查 | `python3 bin/CheckScripts.py` | ✅（13 个脚本 + 新增守卫） |

> **对照样本（规范要求）**：同一次事故里，「本地有文件」与「远端没有文件」两种情况并列——
> 本地 `cannot find symbol` 不出现、远端出现，正是这个对照把根因钉死在 `.gitignore` 上。

## 排除项

| 已排除的可能 | 依据 |
| --- | --- |
| 「工作区干净 ⇒ 文件已入库」 | `.gitignore` 的 `Log/` 吃掉了 `log/` 下的 8 个文件，`git status` 对此**一无所知**（「2.4」） |
| 「git 会提示文件被忽略」 | `git add -A` **静默跳过**、不报错（「2.4」） |
| 「本地测试全绿 ⇒ 提交完整」 | 本地文件在磁盘上、编译测试都正常；**只有 CI** 报 `cannot find symbol`（「2.4」） |
| 「主题表配色错了」 | 根因是**初始化顺序**（界面建好后才应用主题），不是配色（「2.3」） |
| 「这 5 处是实现不出来」 | 都是「想当然」，且**有明确真值**：判重条件 / 清理顺序 / 坐标换算 / 枚举分支 / 取整规则（「2.1」） |
| 「这类问题编译器或测试抓不到」 | 5 处中 **4 处被单测钉住**、**1 处被编译器挡住**（「2.1」） |

## 未决问题

**Q1 「源码未被 `.gitignore` 吃掉」的守卫，覆盖范围够吗？**

现状：只覆盖「落在 `src/` 下 **或** 本身是 `.java`」的忽略文件。
**资源文件**（`.fxml` / `.css` / `.png` / 其它资源）被忽略仍不会被发现——而本项目的静态资源
正是这一类，风险相同。
阻塞点：需要先定「哪些目录算源码树」的口径，再扩守卫。

**Q2 推送前的「产物视角」自证有机器强制吗？**

现状：写进了 `CommitMessageSpec.md` 的「提交前自证」（人工逐条过）。
阻塞点：是否把「新增文件是否入库」也做成提交前脚本，尚未定。

**Q3 真机抓到的两处（宽度溢出、对话框超屏）如何在 CI 里守？**

现状：本轮在冒烟测试里加了「控件宽度不得超过左栏」的断言，但**当时那套测试不进 CI**。
> **进展（2026-10-13）**：issue **#144** / PR #145 已把 GUI 套件接进 CI（`ui-smoke` + Xvfb）。
> **并已确认那条宽度断言当时其实是「空过」的**：它只 `buildRoot()` 却从不放进已显示的 Scene，
> 布局不发生 ⇒ 宽度恒为 0 ⇒ `0 ≤ 220` 永远成立。该断言已在 #145 中改成「真的开一个窗口再断言」。
> —— 这条恰好印证了本档案「本地全绿不等于守住了」的教训。

**Q4 其余对话框是否也会超屏？**

现状：本轮只确认供应商对话框已修（限高 + 滚动），其它对话框**未逐一核对**。
阻塞点：可考虑给所有对话框统一限高（或加冒烟断言）。
