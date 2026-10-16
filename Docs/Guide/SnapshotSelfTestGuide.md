# 快照自测留痕使用指南

**文档版本**：v1.0.1
**状态**：冻结
**生效日期**：2026-10-15
**最后更新**：2026-10-16
**负责人**：@ACANX
**适用版本**：AHA 0.1.x

---

## 变更日志

| 版本 | 日期 | 变更内容 | 变更人 |
|---|---|---|---|
| v1.0.0 | 2026-10-15 | 初版：`/snapshot-assert` 的完整用法与端到端示例（issue #166）| @ACANX / CNXNC |
| v1.0.1 | 2026-10-16 | §7 里工作流的显示名改为 **`SnapshotAssert`**（与文件名一致；原写作 `Snapshot Assert`）| @ACANX / CNXNC |

---

## 1 这是什么

PR 上的试包（**快照包**）拿到本机跑过之后，用**一条评论**把结论记下来：

- 打一条 commit status（名字就是 **`snapshot-assert`**）—— 将来可以（可选）作为合并卡点；
- 在 PR 上留一条**可复核的痕**：版本 / 平台 / JDK / 结论 / 验证人 / 时间 / 对应提交 / token 开销。

三条口径（issue #166，ACANX 2026-10-15 定）：

| 口径 | 含义 |
|---|---|
| **默认启用** | 工作流随仓库就位，发评论即可用 —— 不需要开关、不需要新标签 |
| **默认不卡点** | `snapshot-assert` **不**在 required status checks 里；缺它**不影响**合并。要卡点见 §9 |
| **必测项 0 token** | 必测清单里**没有**任何模型往返（见 §4）；需要真实 provider 请求的动作是**可选**（`+model`）|

> **不替代 CI**：PR 上的构建 / 冒烟 / 静态门禁照旧必须通过；这条机制只记录「人验过机器验不了的部分」。

## 2 什么时候用 · 前置条件

**什么时候用**：拿到某个 PR 的快照包、在本机按清单看过一遍之后 —— 尤其是**改动涉及界面 / 字体 / 主题 / 原生库 / 交互**这类 CI 看不见的地方。

**前置条件**：

| # | 条件 | 说明 |
|---|---|---|
| 1 | 该 PR **已经出过快照包** | 发布页存在 `V<快照版本>-AHA-<目标>-SNAPSHOT` 的 pre-release（怎么出包见 §5.1）|
| 2 | 你对仓库有**写权限** | `OWNER` / `MEMBER` / `COLLABORATOR` 之一；其他人发同一命令只会得到一句「无权限」，**不会**产生 status |
| 3 | 本机能跑起包 | CLI 要 JDK 25；桌面端要图形环境；原生包免 JDK |

## 3 命令一览

```text
/snapshot-assert <版本> <平台> [jdk27] [+model]        # 必测清单通过
/snapshot-assert-fail <版本> <平台> [jdk27] <原因>      # 自测失败（打 failure，失败也要可见）
```

| 参数 | 取值 | 说明 |
|---|---|---|
| `<版本>` | 快照版本号（5 段，如 `0.1.3.00165.10141530`）或 dev 线版本号（4 段，如 `0.1.3.00223`）| 必须能在**发布页**找到对应 tag；若 tag 是快照，还必须属于**本 PR** |
| `<平台>` | `Win` / `MacOS` / `Ubuntu` | 与快照出包同一套词；**必填**（「在 Windows 验过」≠「在 Ubuntu 验过」）|
| `jdk27` | 可选 | 不写即 `jdk25`（与资产命名一致：jdk25 无后缀）|
| `+model` | 可选 | 表示**额外**做了可选的真实 provider 往返（见 §5.8）|

**打出来的 status**：

| 命令 | `state` | `description` |
|---|---|---|
| `/snapshot-assert …` | `success` | `快照 <版本> @ <平台> jdk<JDK>：必测清单通过[（含模型往返）]` |
| `/snapshot-assert-fail …` | `failure` | `快照 <版本> @ <平台> jdk<JDK>：自测失败（详见 PR 评论）` |

`context` 固定为 `snapshot-assert`，`target_url` 指向留痕评论，绑定的提交是**发命令时 PR 的 `head_sha`**。

## 4 必测清单（打标前请逐项过一遍）

**必测（token = 0）**

| 组 | 包 | 项 |
|---|---|---|
| **A** | JVM CLI 便携包 | A1 启动（`version` 与快照版本一致）· A2 **工具直调**（`tool invoke`，绕过模型）· A3 供应商配置（`provider list` / `provider test` / `model show`）· A4 本地命令（`/help`、`/session`）· A5 无 `UnsatisfiedLinkError` |
| **B** | JVM 桌面端便携包 | B1 **开窗**（无 `UnsatisfiedLinkError`、无「JavaFX runtime components are missing」）· B2 亮/暗主题对比 · B3 字体观感 · B4 各界面打开不崩 |
| **C** | 原生镜像包 | C1 单文件免 JDK 能跑 · C2 CLI `--version` / `--help` 退出码 0、桌面端开窗 · C3 与同版本 JVM 包行为对照 |

**为什么必测里没有「真实对话」**：适配器只有 openai-compatible / anthropic / gemini（**没有本地 mock**），
任何真实对话都要打 provider 请求、花 token，而 token 由 **owner 的 key** 承担、快照又是**按 PR 出的**
—— 所以它降级为可选（§5.8）。必测部分用**工具直调** + 配置/本地命令 + 开窗观感，已覆盖「机器验不了」的那部分。

**CI 已覆盖、不重复列**：构建成功 / 包内条目 / `Main-Class` / 无 `module-info` / 本平台原生库 / `java -jar … version` 冒烟。

## 5 端到端完整示例

下面走一遍**CLI JVM 快照**（最常用）。换成桌面端/原生包时，只有「取件」和「清单组」不同，命令与查询完全一样。

### 5.1 第一步：让 PR 出快照包

在目标 PR 上触发（二选一）：

```text
① 给 PR 打标签：SNAPSHOT:Cli
② 在 PR 里发评论：/snapshot Cli
```

等 CI 跑完，到发布页（Releases → 该 pre-release）确认资产出现，tag 形如：

```text
V0.1.3.00165.10141530-AHA-Cli-JVM-SNAPSHOT
```

资产名形如 `aha-cli-0.1.3.00165.10141530.zip`（桌面端为 `aha-desktop-0.1.3.00165.10141530-Win.zip` 之类）。
**把 `0.1.3.00165.10141530` 记下来 —— 下面打标要用它。**

> 没出包？看 CI 的 `SnapshotJVM` / `CliNative` / `DesktopNative` 运行；快照默认保留 7 天，PR 关闭或合并后自动清理。

### 5.2 第二步：取件（Windows，双击或命令行）

```bat
:: JVM 线
Dist\UpdateCliJvmSnapshot.cmd                  :: CLI JVM 快照：取最近一次
Dist\UpdateDesktopJvmSnapshot.cmd              :: 桌面端 JVM 快照：取最近一次
Dist\UpdateCliJvmSnapshot.cmd -List            :: 列出快照（版本 / 目标 / 剩余天数）
Dist\UpdateCliJvmSnapshot.cmd -Pr 225          :: 指定 PR
Dist\UpdateCliJvmSnapshot.cmd -DryRun          :: 只预览：下哪个包、解压到哪

:: 原生线
Dist\UpdateCliNativeSnapshot.cmd
Dist\UpdateDesktopNativeSnapshot.cmd           :: 注意：原生桌面端默认会替换 Dist\aha-desktop-native.exe（旧版留 .bak）
Dist\UpdateDesktopNativeSnapshot.cmd -NoInstall :: 只取件、不安装
```

落地位置：

| 包 | 落地 |
|---|---|
| JVM CLI / JVM 桌面端 / 原生 CLI | 解压到 `Dist\<资产名>\` 的**独立目录**（互不覆盖）|
| 原生桌面端 | 包里 exe 被复制到 `Dist\aha-desktop-native.exe`（旧版备份为 `.bak`），解压目录同时保留 |

### 5.3 第三步：按 A 组清单自测（CLI，0 token）

```bat
cd Dist\V0.1.3.00165.10141530-AHA-Cli-JVM-SNAPSHOT

:: A1 启动
AhaCli.bat version
:: 期望：AHA 0.1.3.00165.10141530        ← 必须与快照版本一致

:: A2 工具直调（绕过模型，0 token）—— 覆盖工具注册 / 参数解析 / 原生库加载
AhaCli.bat tool list
:: 期望：列出内置工具：file-read / file-list / file-write / shell-exec / http-get（退出码 0）
AhaCli.bat tool invoke file-read -p path=README.md
:: 期望：真的把 README.md 内容打出来（不是报「工具不存在」或抛 UnsatisfiedLinkError）
:: 想再验一个需要确认的写类工具：AhaCli.bat tool invoke file-write -p path=/tmp/aha-probe.txt -p content=ok -y

:: A3 供应商配置
AhaCli.bat provider list
AhaCli.bat provider test DeepSeek     :: 按实现**不发起请求**，只打印配置（0 token）
AhaCli.bat model show                 :: 五档模型与默认档

:: A4 本地命令（进对话后敲，或用 --help 侧证）
AhaCli.bat --help
```

A5「无 `UnsatisfiedLinkError`」不用单独测：A2 的工具调用与会话落盘就会触发 `sqlite-jdbc` 的库加载，
**只要上面没抛 `UnsatisfiedLinkError` 即为通过**。

### 5.4 第三步（桌面端）：按 B 组清单自测（0 token）

```bat
cd Dist\V0.1.3.00165.10141530-AHA-Desktop-JVM-SNAPSHOT
AhaDesktop.bat
```

人工看四件事（**不需要对话**）：

| 项 | 怎么看 |
|---|---|
| B1 开窗 | 窗口出现；控制台无 `UnsatisfiedLinkError`、无「JavaFX runtime components are missing」 |
| B2 主题对比 | 亮/暗来回切一次：标题与背景、输入框与页面底色**对比正常**（无黑底黑字）|
| B3 字体观感 | 与 JVM 模式（或上一版）对照，无明显发虚/锯齿劣化 |
| B4 界面不崩 | 设置 / 供应商面板 / 会话列表各打开一次 |

### 5.5 第四步：打标

在该 PR 里发一条评论（**只有这一条命令，别加别的内容**）：

```text
/snapshot-assert 0.1.3.00165.10141530 Win
```

（若在 macOS 验的写 `MacOS`；验的是 jdk27 包则加 `jdk27`。）

### 5.6 第五步：核对结果（可复核）

**① 看评论**：PR 下出现一条 bot 评论（marker `<!-- snapshot-assert -->`，重复命令只**更新**它）：

```markdown
### 快照自测留痕

| 项 | 值 |
|---|---|
| 快照版本 | `0.1.3.00165.10141530`（tag `V0.1.3.00165.10141530-AHA-Cli-JVM-SNAPSHOT`）|
| 平台 / JDK | Win / jdk25 |
| 结论 | ✅ 必测清单通过 |
| token 开销 | 0 |
| 验证人 / 时间 | @acanx / 2026-10-15 20:10 +0800 |
| 对应提交 | `a1b2c3d…`（PR 再 push 后新 sha 上没有这条 status，本条自动失效）|
| 断言清单 | A1–A5 / B1–B4 / C1–C3（按取到的包种类适用；原文见 `ReleaseProcess.md` §2.2）|
| 可选 M 组建议 | 命中模型 / 工具 / 会话 / 视图链路 ⇒ 建议做可选 M1/M2（命令末尾加 +model）|
```

**② 看 status**（把 `<sha>` 换成 PR 当前 head）：

```bash
gh api repos/ACANX/AHA/commits/<sha>/statuses --jq '.[0] | {context,state,description}'
# {
#   "context": "snapshot-assert",
#   "state": "success",
#   "description": "快照 0.1.3.00165.10141530 @ Win jdk25：必测清单通过"
# }
```

**③ 看绑定的是哪个提交**：

```bash
gh api repos/ACANX/AHA/pulls/225 --jq .head.sha
```

### 5.7 自测没过怎么写

失败**也要留痕**（否则 PR 上什么都不显示，别人会以为没人验）：

```text
/snapshot-assert-fail 0.1.3.00165.10141530 Win 工具调用时崩：UnsatisfiedLinkError（sqlite-jdbc 的库没解出来）
```

效果：status 打 `failure`，description 写「自测失败（详见 PR 评论）」，**原因原文**落在留痕评论里。

### 5.8 可选 M 组（会花 token，按需）

工作流会按 PR 改动文件给提示（命中 `llm/` / `tool/` / `session/` / `view/` / `*Adapter*` 就建议做）。
做了就在命令末尾加 `+model`：

```text
/snapshot-assert 0.1.3.00165.10141530 Win +model
```

| 项 | 内容 |
|---|---|
| M1 真实往返 | 发**一句话**，真实 provider 有回复（不是本地回显）|
| M2 模型驱动的工具调用 | 让模型自己决定调一次工具并成功 |
| M3 边界观感 | 长会话滚动 / 大文件渲染 / 并发等 |

省钱口径：**只发一句话** · 先把默认档切到最便宜的档（`AhaCli.bat model use Flash`）· **失败即止**，别重试刷请求。

## 6 平台与 JDK 怎么写

| 场景 | 写法 |
|---|---|
| Windows + JDK 25 | `/snapshot-assert 0.1.3.00165.10141530 Win` |
| Ubuntu + JDK 27 | `/snapshot-assert 0.1.3.00165.10141530 Ubuntu jdk27` |
| macOS（arm64）+ 含模型往返 | `/snapshot-assert 0.1.3.00165.10141530 MacOS +model` |

平台词与快照出包**同一套**（`Win` / `MacOS` / `Ubuntu`）；资产名里也是这套（`…-Win.zip` / `…-Ubuntu.zip` / `…-MacOS.zip`）。

## 7 常见拒绝与怎么办

命令被拒绝时，bot 会回复一条**带用法与清单**的说明（marker `<!-- snapshot-assert-usage -->`），且**不会**产生 status。

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 「版本号不合法」 | 版本号写错/缺段 | 用快照版本号（5 段）或 dev 线版本号（4 段）|
| 「平台词必须是 `Win` / `MacOS` / `Ubuntu`」 | 写成 `Windows`、`win` 等 | 按表写（大小写敏感）|
| 「发布页上没有与 `V…` 对应的 tag」 | 版本写错，或快照**还没出好** | 去发布页核对 tag；必要时先按 §5.1 出包 |
| 「快照 `…` 属于 PR #N，**不是**本 PR」 | 拿了别的 PR 的版本 | 用**本 PR** 的快照版本 |
| 「忽略：… 没有写权限」 | 你不是 `OWNER`/`MEMBER`/`COLLABORATOR` | 请有权限的人发 |
| `/snapshot-assert-fail` 说需要写原因 | 缺原因 | 补一句：`… Win 工具调用崩溃` |
| 打了标，但分支保护页看不到 | 名字不对，或还没加进 required | 核对 `snapshot-assert`；加 required 见 §9 |
| 推了新提交后标不见了 | **正常**（见 §8）| 重新自测并重新打标 |

## 8 失效与重打

- status 绑在**发命令时** PR 的 `head_sha` 上 ⇒ **之后再 push，新 sha 上没有它**（旧 sha 上仍在）；
- 同一个 sha 上重复打标是**覆盖**（同一个 `context`），评论也是**更新同一条**（按 marker）；
- 所以：**改了代码就重打；换平台也要重打**（见下）。

> ⚠️ **已知边界**：一条 status 只能有**一个** description，留痕评论也只有一条 ——
> 同一 sha 上先验 `Win` 再验 `Ubuntu`，**后一次会覆盖前一次**（记录里只剩最后一次）。
> 需要同时留两个平台的结论时：把它们写进同一条命令的语境里（例如分别在不同 sha / 不同 PR 上验），
> 或把第二个平台的结果补进 PR 评论正文由人工阅读。

## 9 想让「自测通过」变成卡点（可选，默认不做）

**默认不卡点**。要卡点时，由 ACANX 在分支保护里把 **`snapshot-assert`** 加为 required status check：

```bash
# 查看现状（需要仓库管理权限）
gh api repos/ACANX/AHA/branches/dev/protection --jq .required_status_checks.contexts
```

加进去之后：**缺这条 status 的 PR 无法合并**（这就是「卡点」）。想撤销就把该 context 去掉。
—— 这一步**不是工作流的事**（本仓库不在代码里改分支保护设置），且建议等机制跑稳一段再决定。

## 10 边界与 FAQ

| 问题 | 回答 |
|---|---|
| 机器会不会替我判断「界面好不好看」？ | 不会。这是**人工断言**，机器只负责**记录与传导**；但清单是**明确**的（§4），不是一句「我看着行」|
| 为什么必测项不许花 token？ | token 由 **owner 的 provider key** 承担，而快照是**按 PR** 出的 —— 花 token 的动作必须明示为可选（`+model`）|
| 没有写权限的人能打标吗？ | 不能；只会得到一句「无权限」，**不产生** status |
| 一次命令能验多个平台吗？ | 不能；一条命令一个平台（见 §8 的已知边界）|
| 能不能逐项打标（`/snapshot-assert A1 A2 …`）？ | 不做 —— 逐项打 status 会把分支保护页刷满；逐项结论写进留痕评论即可 |
| 纯自动化的检查呢？ | 那是 CI（`build (…)` / `Gate`）的职责，与本机制无关；本机制只补「机器验不了」的部分 |
| 命令不生效/没人理？ | 确认评论**挂在 PR 上**、且以 `/snapshot-assert` 开头；查 Actions 里的 **SnapshotAssert** 工作流运行记录 |

## 11 相关文件与文档

| 位置 | 内容 |
|---|---|
| `.github/workflows/SnapshotAssert.yml` | 实现（`issue_comment` 触发 → 校验 → 打 status → 幂等回写评论）|
| `bin/CheckScripts.py::check_snapshot_assert_contract()` | 静态守卫：`context` / 触发守卫里的命令 / top-level `permissions` / 三条 peer 工作流的排除 / 清单编号一致 / 必测行 token 必须为 0 |
| `Docs/DevSpec/ReleaseProcess.md` §2.2 | **清单原文**与口径（工作流内置同一份编号）|
| `Docs/Guide/CommandCheatsheet.md` §7 | 一句话速查（含本指南链接）|
| `.github/Python/SnapshotComment.py` | 幂等回写评论（含瞬时错误有界重试）|
| issue [#166](https://github.com/ACANX/AHA/issues/166) | 需求、口径变更与验收标准 |
